package app.mintcal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.mintcal.core.change.Change
import app.mintcal.core.change.ChangeSet
import app.mintcal.core.model.Course
import app.mintcal.core.model.Source
import app.mintcal.data.MintDb
import app.mintcal.data.Repository
import app.mintcal.data.SyncTokenVault
import app.mintcal.sync.SyncManager
import app.mintcal.sync.SyncStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 端到端同步测试：两台「设备」（两份独立的本地数据库）对着真实运行的 Go 服务端同步。
 * 需要设置环境变量 MINTCAL_TEST_SERVER / MINTCAL_TEST_USER / MINTCAL_TEST_PASS（见 scripts/sync-e2e.sh），
 * 没有设置时自动跳过。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = MintCalApp::class)
class SyncIntegrationTest {
    private class MemoryVault : SyncTokenVault {
        private var token: String? = null

        override fun syncToken() = token

        override fun setSyncToken(plain: String) {
            token = plain.ifEmpty { null }
        }
    }

    private class Device(
        ctx: Context,
        name: String,
    ) {
        val repo = Repository(MintDb(ctx, "$name.db"))
        val sync = SyncManager(repo, SyncStore(ctx, MemoryVault(), "sync-$name"), CoroutineScope(Job()))
    }

    private fun course(
        semesterId: String,
        name: String,
    ) = Course(
        app.mintcal.core.util.Ids
            .next(),
        semesterId,
        name,
        dayOfWeek = 1,
        startPeriod = 1,
        endPeriod = 2,
        weeks = setOf(1, 2, 3),
    )

    private fun add(
        repo: Repository,
        c: Course,
    ) = runBlocking {
        repo.commit(
            ChangeSet(
                app.mintcal.core.util.Ids
                    .next(),
                "add",
                Source.MANUAL,
                System.currentTimeMillis(),
                listOf(Change.Add(c)),
            ),
        )
    }

    private fun names(repo: Repository) =
        repo.snapshot.value
            .live()
            .courses
            .map { it.name }
            .sorted()

    @Test
    fun twoDevicesConverge() {
        val server = System.getenv("MINTCAL_TEST_SERVER")
        val user = System.getenv("MINTCAL_TEST_USER")
        val pass = System.getenv("MINTCAL_TEST_PASS")
        assumeNotNull(server, user, pass)
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        runBlocking {
            val a = Device(ctx, "devA")
            val b = Device(ctx, "devB")
            a.repo.load()
            b.repo.load()

            // A 先建数据并登录：把学期和课程推上去
            val semA = a.repo.snapshot.value.currentSemester!!
            val math = course(semA.id, "高数")
            add(a.repo, math)
            a.sync.login(server, user, pass)

            // B 是全新的设备，只有自动创建的默认学期：登录后应丢弃它，得到 A 的学期与课程，且只有一个学期
            b.sync.login(server, user, pass)
            assertEquals(listOf("高数"), names(b.repo))
            assertEquals(
                1,
                b.repo.snapshot.value
                    .live()
                    .semesters.size,
            )
            assertEquals(
                semA.id,
                b.repo.snapshot.value.currentSemester!!
                    .id,
            )

            // B 改名，A 同步后看到新名字
            val bMath =
                b.repo.snapshot.value.courses
                    .single { it.id == math.id }
            runBlocking {
                b.repo.commit(
                    ChangeSet(
                        app.mintcal.core.util.Ids
                            .next(),
                        "rename",
                        Source.MANUAL,
                        System.currentTimeMillis(),
                        listOf(Change.Modify(bMath, bMath.copy(name = "高等数学"))),
                    ),
                )
            }
            b.sync.syncNow()
            a.sync.syncNow()
            assertEquals(listOf("高等数学"), names(a.repo))

            // A 删除，B 同步后课程消失（墓碑）
            val aMath =
                a.repo.snapshot.value.courses
                    .single { it.id == math.id }
            a.repo.commit(
                ChangeSet(
                    app.mintcal.core.util.Ids
                        .next(),
                    "del",
                    Source.MANUAL,
                    System.currentTimeMillis(),
                    listOf(Change.Remove(aMath)),
                ),
            )
            a.sync.syncNow()
            b.sync.syncNow()
            assertEquals(emptyList<String>(), names(b.repo))

            // A 新增后同步，再撤销（物理移除），同步后 B 也不应该再有它
            val extra = course(semA.id, "临时课")
            val cs = add(a.repo, extra)
            a.sync.syncNow()
            b.sync.syncNow()
            assertEquals(listOf("临时课"), names(b.repo))
            assertTrue(a.repo.undo(cs.id))
            a.sync.syncNow()
            b.sync.syncNow()
            assertEquals(emptyList<String>(), names(b.repo))

            // 退出登录后回到本地模式，本地数据保留
            b.sync.logout()
            assertTrue(b.sync.state.value.account == null)
            assertEquals(
                1,
                b.repo.snapshot.value
                    .live()
                    .semesters.size,
            )
        }
    }
}
