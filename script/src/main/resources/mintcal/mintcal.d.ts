// 薄荷日程 MintCal · 高级修改 SDK 类型声明
// 脚本在沙箱中同步执行：没有网络、文件、定时器；通过全局对象 mc 读写数据，结束时可 return 一个结果。
// 日期统一用字符串："YYYY-MM-DD"；日期时间用 "YYYY-MM-DDTHH:mm"。dayOfWeek：1=周一 … 7=周日。

type WeeksInput = number[] | string; // 如 [1,2,3] 或 "1-16"、"1-16单"、"2-16双"、"1,3,5-9"

interface Semester { id: string; name: string; week1Monday: string; totalWeeks: number; periods: { index: number; start: string; end: string }[] }

interface Course {
  id: string; semesterId: string; name: string; teacher: string; room: string;
  dayOfWeek: number; startPeriod: number; endPeriod: number; weeks: number[];
  colorIndex: number; remindMinutes: number | null; note: string;
}
interface CourseInput {
  name: string; teacher?: string; room?: string;
  dayOfWeek?: number; day?: number; startPeriod: number; endPeriod?: number;
  weeks: WeeksInput; colorIndex?: number; remindMinutes?: number | null; note?: string;
}

interface Recurrence {
  freq: 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY'; interval?: number;
  byDays?: number[]; until?: string | null; count?: number | null; exDates?: string[];
}
interface CalEvent {
  id: string; title: string; start: string; end: string; allDay: boolean; location: string; note: string;
  colorIndex: number; category: string; remindMinutes: number | null; recurrence: Recurrence | null;
  kind: 'NORMAL' | 'EXAM' | 'COUNTDOWN';
}
interface EventInput {
  title: string; start: string; end?: string; allDay?: boolean; location?: string; note?: string;
  colorIndex?: number; category?: string; remindMinutes?: number | null; recurrence?: Recurrence | null;
  kind?: 'NORMAL' | 'EXAM' | 'COUNTDOWN';
}

interface Task {
  id: string; title: string; note: string; listId: string | null; tags: string[]; priority: 0 | 1 | 2 | 3;
  startDate: string | null; due: string | null; dueHasTime: boolean; estimateMinutes: number | null;
  done: boolean; parentId: string | null; repeat: Recurrence | null; remindMinutes: number | null;
  courseId: string | null; dueBeforeNextClass: boolean; blockStart: string | null; blockMinutes: number | null;
}
type TaskInput = Partial<Omit<Task, 'id'>> & { title: string };

interface TaskList { id: string; name: string; colorIndex: number }
interface CourseException {
  id: string; courseId: string; week: number; type: 'CANCEL' | 'RESCHEDULE';
  newWeek: number | null; newDayOfWeek: number | null; newStartPeriod: number | null; newEndPeriod: number | null; newRoom: string | null;
}

/**
 * 查询条件：
 *  - 字符串：按 id 精确匹配；函数：返回 true 的记录；
 *  - 对象：每个字段都要满足。文本字段（name/title/teacher/room/location/note/category）里的字符串按「包含」匹配，
 *    正则按 test 匹配，数组字段（如 weeks）给标量表示「包含该值」，给数组表示「全部包含」，其他字段严格相等。
 */
type Query<T> = string | ((item: T) => boolean) | { [K in keyof T]?: T[K] | RegExp | ((v: T[K], item: T) => boolean) | T[K][] };

interface Collection<T, I> {
  query(q?: Query<T>): T[];
  get(id: string | { id: string }): T | null;
  count(q?: Query<T>): number;
  create(input: I): T;
  /** 修改：update(id, 部分字段)，或 update(整个对象)；返回修改后的副本 */
  update(idOrObj: string | { id: string }, patch?: Partial<T>): T;
  save(obj: T): T;
  /** 删除，返回是否删除成功（删除课程会同步删除其调课记录，删除待办会一并删除子任务） */
  remove(idOrObj: string | { id: string }): boolean;
  removeWhere(q: Query<T>): number;
}

interface CourseCollection extends Collection<Course, CourseInput> {
  /** 某周停课 */
  cancel(course: string | Course, week: number): CourseException;
  /** 某周调课：可改周次 / 星期 / 节次 / 教室 */
  reschedule(course: string | Course, week: number, to: { week?: number; dayOfWeek?: number; day?: number; startPeriod?: number; endPeriod?: number; room?: string }): CourseException;
  /** 取消某周的停课或调课 */
  restore(course: string | Course, week: number): boolean;
}
interface TaskCollection extends Collection<Task, TaskInput> {
  complete(idOrObj: string | Task, done?: boolean): Task;
}

declare const mc: {
  /** 今天的日期 */
  today: string;
  /** 今天所在的教学周（可能 ≤0 或超过总周数） */
  currentWeek: number | null;
  semester: Semester | null;
  periods: { index: number; start: string; end: string }[];
  courses: CourseCollection;
  events: Collection<CalEvent, EventInput>;
  tasks: TaskCollection;
  lists: Collection<TaskList, { name: string; colorIndex?: number }>;
  exceptions: Collection<CourseException, never>;
  updateSemester(patch: { name?: string; week1Monday?: string; totalWeeks?: number }): Semester;
  /** 第 week 周星期 day 的日期 */
  dateOf(week: number, day?: number): string;
  /** 某日期属于第几周 */
  weekOf(date: string): number;
  /** 解析周次表达式 */
  weeks(input: WeeksInput): number[];
  uuid(): string;
};
declare const console: { log(...a: any[]): void; info(...a: any[]): void; warn(...a: any[]): void; error(...a: any[]): void };
