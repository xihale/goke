package app.mintcal.core.jxfw

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JwcwxParseTest {
    @Test
    fun parseBuildingsFromFixtureExcerpt() {
        val raw =
            """
            {
              "code": 0,
              "data": [
                {"jzwbh":"05","jzwdm":"0005","jzwmc":"教学三号楼(城)","szxqdm":"1","xqmc":"大学城校区"},
                {"jzwbh":"29","jzwdm":"0029","jzwmc":"教学楼","szxqdm":"2","xqmc":"龙洞校区"},
                {"jzwbh":"18","jzwdm":"0018","jzwmc":"教学楼","szxqdm":"3","xqmc":"东风路校区"},
                {"jzwbh":"25","jzwdm":"0025","jzwmc":"教学楼","szxqdm":"4","xqmc":"番禺校区"},
                {"jzwbh":"38","jzwdm":"108446977","jzwmc":"教学一号楼(揭)","szxqdm":"108291461","xqmc":"揭阳校区"}
              ],
              "message": "成功"
            }
            """.trimIndent()

        val buildings = JwcwxParse.parseBuildings(raw)
        assertEquals(5, buildings.size)
        assertEquals(
            ClassroomBuilding(
                code = "0005",
                number = "05",
                name = "教学三号楼(城)",
                campusCode = "1",
                campusName = "大学城校区",
            ),
            buildings[0],
        )
        assertEquals("108446977", buildings[4].code)
        assertEquals("108291461", buildings[4].campusCode)
        assertTrue(JwcwxParse.parseBuildings("not json").isEmpty())
    }

    @Test
    fun parseRoomsHandlesMultilineTdAttributesAndSkipsHeaderRow() {
        val html =
            """
            <table id="js-table">
                <tr>
                    <th class="js">教室</th>
                    <th style="width: 13%">0102</th>
                    <th style="width: 13%">0304</th>
                </tr>
                <tr>
                    <th class="js">教3-101</th>
                    <td data-rnrs="223" 
                    class="qk-011030101-0102 qk-011030101-01 qk-011030101-02 qk-011030101-">
                        <i class="fa fa-check kx" data-rnrs="223"></i>
                        </td>
                    <td data-rnrs="223" 
                    class="qk-011030101-0304 qk-011030101-03 qk-011030101-04 qk-011030101-">
                        <i class="fa fa-check kx" data-rnrs="223"></i>
                        </td>
                </tr>
                <tr>
                    <th class="js">教3-102</th>
                    <td data-rnrs="180"
                    class="qk-011030102-0102 qk-011030102-01 qk-011030102-02">
                        <i class="fa fa-check kx" data-rnrs="180"></i>
                    </td>
                </tr>
            </table>
            """.trimIndent()

        val rooms = JwcwxParse.parseRooms(html)
        assertEquals(
            listOf(
                ClassroomRoom(code = "011030101", name = "教3-101", capacity = 223),
                ClassroomRoom(code = "011030102", name = "教3-102", capacity = 180),
            ),
            rooms,
        )
        assertTrue(JwcwxParse.parseRooms("<table><tr><th class=\"js\">教室</th></tr></table>").isEmpty())
    }

    @Test
    fun parseOccupancyHandlesNullablesSytypesAndPeriodFallback() {
        val raw =
            """
            {
              "code": 0,
              "data": [
                {
                  "dm": "3404218",
                  "jcdm": "020304",
                  "jcdm2": "02,03,04",
                  "jxbmc": "教学班482",
                  "jxcddm": "011030201",
                  "jxcdmc": "教3-201",
                  "kcmc": "电影与深层心理学",
                  "rs": 108,
                  "sknrjj": "五、好莱坞电影的批判性读解",
                  "sytype": "1",
                  "teaxms": "田昊",
                  "xq": "7",
                  "zc": "12"
                },
                {
                  "dm": "4757167",
                  "jcdm": "010203040506070809101112",
                  "jcdm2": "01,02,03,04,05,06,07,08,09,10,11,12",
                  "jxbmc": null,
                  "jxcddm": "011030304",
                  "jxcdmc": null,
                  "kcmc": null,
                  "rs": null,
                  "sknrjj": "星耀工大校园之星彩排",
                  "sytype": "2",
                  "teaxms": "肖家祺",
                  "xq": "7",
                  "zc": "12"
                },
                {
                  "dm": "9000001",
                  "jcdm": "0506",
                  "jcdm2": null,
                  "jxbmc": null,
                  "jxcddm": "011030101",
                  "jxcdmc": null,
                  "kcmc": null,
                  "rs": null,
                  "sknrjj": null,
                  "sytype": "3",
                  "teaxms": null,
                  "xq": "2",
                  "zc": "13"
                },
                {
                  "dm": "100134448",
                  "jcdm": "0304",
                  "jcdm2": "03,04",
                  "jxbmc": null,
                  "jxcddm": "011030109",
                  "jxcdmc": null,
                  "kcmc": null,
                  "rs": 1,
                  "sknrjj": null,
                  "sytype": "4",
                  "teaxms": "梁可湘",
                  "xq": "1",
                  "zc": "13"
                },
                {
                  "dm": "9000002",
                  "jcdm": "0809",
                  "jcdm2": "",
                  "jxcddm": "011030102",
                  "sytype": "9",
                  "xq": "5",
                  "zc": "13"
                }
              ],
              "message": "查询数据成功"
            }
            """.trimIndent()

        val list = JwcwxParse.parseOccupancy(raw)
        assertEquals(5, list.size)

        assertEquals(
            ClassroomOccupancy(
                id = "3404218",
                roomCode = "011030201",
                roomName = "教3-201",
                periods = listOf(2, 3, 4),
                periodCode = "020304",
                courseName = "电影与深层心理学",
                teacher = "田昊",
                className = "教学班482",
                note = "五、好莱坞电影的批判性读解",
                dayOfWeek = 7,
                week = 12,
                type = OccupancyType.COURSE,
                count = 108,
            ),
            list[0],
        )

        assertEquals(OccupancyType.BORROWED, list[1].type)
        assertEquals((1..12).toList(), list[1].periods)
        assertEquals("", list[1].courseName)
        assertEquals("", list[1].roomName)
        assertNull(list[1].count)

        assertEquals(OccupancyType.LOCKED, list[2].type)
        assertEquals(listOf(5, 6), list[2].periods)
        assertEquals("", list[2].note)

        assertEquals(OccupancyType.EXAM, list[3].type)
        assertEquals(listOf(3, 4), list[3].periods)
        assertEquals(1, list[3].count)
        assertEquals("梁可湘", list[3].teacher)

        assertEquals(OccupancyType.OTHER, list[4].type)
        assertEquals(listOf(8, 9), list[4].periods)
    }

    @Test
    fun parseBuildingsOrThrowAndParseOccupancyOrThrowRejectErrorsAndNullData() {
        val errJson = """{"code":-1,"data":null,"message":"未绑定"}"""
        assertTrue(JwcwxParse.parseBuildings(errJson).isEmpty())
        assertTrue(JwcwxParse.parseOccupancy(errJson).isEmpty())

        val bErr = assertFailsWith<IllegalStateException> { JwcwxParse.parseBuildingsOrThrow(errJson) }
        assertTrue("code=-1" in bErr.message.orEmpty())
        assertTrue("未绑定" in bErr.message.orEmpty())

        val oErr = assertFailsWith<IllegalStateException> { JwcwxParse.parseOccupancyOrThrow(errJson) }
        assertTrue("code=-1" in oErr.message.orEmpty())
        assertTrue("未绑定" in oErr.message.orEmpty())

        assertFailsWith<IllegalStateException> { JwcwxParse.parseBuildingsOrThrow("""{"code":0,"data":null}""") }
        assertFailsWith<IllegalStateException> { JwcwxParse.parseOccupancyOrThrow("not json") }
    }
}
