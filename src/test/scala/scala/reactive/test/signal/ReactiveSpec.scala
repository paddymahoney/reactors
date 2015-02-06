package scala.reactive
package test.signal



import scala.collection._
import org.scalatest._
import org.scalatest.matchers.ShouldMatchers



class ReactiveSpec extends FlatSpec with ShouldMatchers {

  class ReactiveTest {
    val x = RCell(0)
    val y = RCell(0)
    val z = RCell(0)
    val w = RCell(0)
  }

  "A reactive" should "be filtered" in {
    val rt = new ReactiveTest
    val s = rt.x.filter {
      _ % 2 == 0
    }
    val a = s foreach { case x =>
      assert(x % 2 == 0)
    }

    rt.x := 1
    rt.x := 2
  }

  it should "be mapped" in {
    val e = new Reactive.Emitter[Int]
    val s = e.map {
      _ + 1
    }
    val a = s foreach { case x =>
      assert(x == 2)
    }

    e += 1
  }

  it should "emit once" in {
    val e = new Reactive.Emitter[Int]
    val s = e.once
    val check = mutable.Buffer[Int]()
    val adds = s.foreach(check += _)

    e += 1
    e += 2
    e += 3

    assert(check == Seq(1))
  }

  it should "be traversed with foreach" in {
    val e = new Reactive.Emitter[Int]
    val buffer = mutable.Buffer[Int]()
    val s = e.foreach(buffer += _)

    e += 1
    e += 2
    e += 3

    buffer should equal (Seq(1, 2, 3))
  }

  it should "be scanned past" in {
    val cell = RCell(0)
    val s = cell.scanPast(List[Int]()) { (acc, x) =>
      x :: acc
    }
    val a = s foreach { case xs =>
      assert(xs.reverse == Stream.from(1).take(xs.length))
    }

    cell := 1
    cell := 2
    cell := 3
    cell := 4
    cell := 5
  }

  it should "come after" in {
    val e = new Reactive.Emitter[Int]
    val start = new Reactive.Emitter[Boolean]
    val buffer = mutable.Buffer[Int]()
    val s = (e after start) foreach { case x => buffer += x }

    e += 1
    e += 2
    e += 3
    start += true
    e += 4
    e += 5
    e += 6
    start += false
    e += 7
    e += 8
    buffer should equal (Seq(4, 5, 6, 7, 8))
  }

  it should "never come after" in {
    val e = new Reactive.Emitter[Int]
    val start = Reactive.Never[Int]
    val buffer = mutable.Buffer[Int]()
    val s = (e after start) foreach { case x => buffer += x }

    e += 1
    e += 2
    e += 3
    buffer should equal (Seq())
  }

  it should "occur until" in {
    val e = new Reactive.Emitter[Int]
    val end = new Reactive.Emitter[Boolean]
    val buffer = mutable.Buffer[Int]()
    val s = (e until end) foreach { x => buffer += x }

    e += 1
    e += 2
    end += true
    e += 3
    end += false
    e += 4
    buffer should equal (Seq(1, 2))
  }

  it should "be union" in {
    val xs = new Reactive.Emitter[Int]
    val ys = new Reactive.Emitter[Int]
    val buffer = mutable.Buffer[Int]()
    val s = (xs union ys) foreach { case x => buffer += x }

    xs += 1
    ys += 11
    xs += 2
    ys += 12
    ys += 15
    xs += 7
    buffer should equal (Seq(1, 11, 2, 12, 15, 7))
  }

  it should "be concat" in {
    import Permission.canBuffer
    val xs = new Reactive.Emitter[Int]
    val closeXs = new Reactive.Emitter[Unit]
    val ys = new Reactive.Emitter[Int]
    val buffer = mutable.Buffer[Int]()
    val s = ((xs until closeXs) concat ys) foreach { case x => buffer += x }

    xs += 1
    ys += 11
    xs += 2
    ys += 12
    ys += 15
    xs += 7
    closeXs += ()
    buffer should equal (Seq(1, 2, 7, 11, 12, 15))
  }

  def testSynced() {
    import Permission.canBuffer
    val xs = new Reactive.Emitter[Int]
    val ys = new Reactive.Emitter[Int]
    val synced = (xs sync ys) { _ + _ }
    val buffer = mutable.Buffer[Int]()
    val s = synced foreach { case x => buffer += x }

    for (i <- 0 until 200) xs += i
    for (j <- 200 to 51 by -1) ys += j
    buffer.size should equal (150)
    for (x <- buffer) x should equal (200)
  }

  it should "be synced" in {
    testSynced()
  }

  class Cell(var x: Int = 0)

  it should "mutate" in {
    val ms = Signal.Mutable(new Cell)
    val vals = ms.map(_.x).signal(0)
    val e = new RCell[Int](0)
    e.mutate(ms) {
      ms().x = _
    }

    e := 1
    assert(vals() == 1)
    e := 2
    assert(vals() == 2)
  }

  it should "be muxed" in {
    val cell = RCell[Reactive[Int]](Signal.Constant(0))
    val e1 = new Reactive.Emitter[Int]
    val e2 = new Reactive.Emitter[Int]
    val ints = cell.mux().signal(0)

    assert(ints() == 0, ints())
    cell := e1
    e1 += 10
    assert(ints() == 10, ints())
    e1 += 20
    assert(ints() == 20, ints())
    e2 += 30
    assert(ints() == 20, ints())
    cell := e2
    assert(ints() == 20, ints())
    e2 += 40
    assert(ints() == 40, ints())
    e1 += 50
    assert(ints() == 40, ints())
    e2 += 60
    assert(ints() == 60, ints())
  }

  it should "be higher-order union" in {
    val cell = RCell[Reactive[Int]](Signal.Constant(0))
    val e1 = new Reactive.Emitter[Int]
    val e2 = new Reactive.Emitter[Int]
    val e3 = new Reactive.Emitter[Int]
    val e4 = new Reactive.Emitter[Int]
    val closeE4 = new Reactive.Emitter[Unit]
    val buffer = mutable.Buffer[Int]()
    val s = cell.union() foreach { case x => buffer += x }

    e1 += -1
    e2 += -2
    e3 += -3

    cell := e1
    e1 += 1
    e1 += 11
    e2 += -22
    e3 += -33
    cell := e2
    e1 += 111
    e2 += 2
    e3 += -333
    cell := e3
    e1 += 1111
    e2 += 22
    e3 += 3
    cell := e1
    e1 += 11111
    e2 += 222
    e3 += 33
    cell := (e4 until closeE4)
    e4 += 4
    closeE4 += ()
    e4 += -44
    buffer should equal (Seq(1, 11, 111, 2, 1111, 22, 3, 11111, 222, 33, 4))
  }

  it should "be higher-order concat" in {
    import Permission.canBuffer
    val cell = RCell[Reactive[Int]](Signal.Constant(0))
    val e1 = new Reactive.Emitter[Int]
    val closeE1 = new Reactive.Emitter[Unit]
    val e2 = new Reactive.Emitter[Int]
    val closeE2 = new Reactive.Emitter[Unit]
    val e3 = new Reactive.Emitter[Int]
    val closeE3 = new Reactive.Emitter[Unit]
    val e4 = new Reactive.Emitter[Int]
    val buffer = mutable.Buffer[Int]()
    val s = cell.concat() foreach { x => buffer += x }

    e1 += -1
    e2 += -2
    e3 += -3

    cell := e1 until closeE1
    e1 += 1
    e1 += 11
    e2 += -22
    e3 += -33
    cell := e2 until closeE2
    e1 += 111
    e2 += 2
    e3 += -333
    cell := e3 until closeE3
    e1 += 1111
    e2 += 22
    e3 += 3
    closeE1 += ()
    closeE2 += ()
    e1 += -11111
    e2 += -222
    e3 += 33
    closeE3 += ()
    e3 += -333
    cell := e4
    e4 += 4

    buffer should equal (Seq(1, 11, 111, 1111, 2, 22, 3, 33, 4))
  }

  it should "collect accurately" in {
    val e = new Reactive.Emitter[String]
    val evens = e collect {
      case x if x.toInt % 2 == 0 => x
    }
    val observed = mutable.Buffer[String]()
    val emitSub = evens.foreach(observed += _)

    for (i <- 0 until 100) e += i.toString

    observed should equal ((0 until 100 by 2).map(_.toString))
  }

}
