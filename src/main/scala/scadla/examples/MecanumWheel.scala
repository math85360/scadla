package scadla.examples

import math._
import scadla._
import utils._
import InlineOps._

/** A class for the small rollers in the mecanum wheel */
class Roller(height: Double, maxOuterRadius: Double,minOuterRadius: Double, innerRadius: Double) {

  val axis = 0.5
  val h = height - 2*axis

  protected def carveAxle(s: Solid) = s - Cylinder(innerRadius, height)

  def outline = {
    // r * f = maxOuterRadius
    // r * f * cos(a) = minOuterRadius
    // r * sin(a) = height/2
    val a = math.acos(minOuterRadius/maxOuterRadius)
    val r = h / 2 / math.sin(a)
    val f = maxOuterRadius / r
    val s = Sphere(r).scale(f, f, 1).moveZ(height/2)
    val c1 = Cylinder(maxOuterRadius, h).moveZ(axis)
    val c2 = Cylinder(minOuterRadius, height)
    (s * c1) + c2
  }

  def solid = carveAxle(outline)

  //to make only the "skeleton" of a roller,
  //then it can be coated with oogoo to get better friction
  def skeleton = {
    val base =
      carveAxle(
        Cylinder(minOuterRadius, height) +
        solid.scale(0.8, 0.8, 1)
      )
    val angle = math.Pi / 8
    val grooveDepth = max(maxOuterRadius - minOuterRadius, 2)
    val inner = max(maxOuterRadius - grooveDepth, (minOuterRadius + innerRadius) / 2)
    val slice = PieSlice(maxOuterRadius, inner, angle, h).moveZ(axis)
    (0 until 8).foldLeft(base)( (acc, i) => acc - slice.rotateZ(i*2*angle) )
  }

  //mold for k*l roller
  //TODO put some grove to let the additional oogoo escape
  def mold(k: Int, l: Int) = {
    val wall = 2
    val distToWall = wall + maxOuterRadius
    val step = maxOuterRadius + distToWall
    val flatRoller = Rotate(-math.Pi/2, 0, 0, outline)
    val row = {
      val rs = for (i <- 0 until k) yield Translate( distToWall + i*step, 1, distToWall, flatRoller)
      Union(rs:_*)
    }
    val rows = {
      val rs = for (j <- 0 until l) yield row.moveY(j*(2+height))
      Union(rs:_*)
    }
    val base = Cube(k*step+wall, l*(2+height), distToWall)
    base - rows
  }

}


class MecanumWheel(radius: Double, width: Double, angle: Double, nbrRollers: Int) {

  //TODO ideally the projection of the arc created by the roller should match the shape of the wheel

  //some more parameters
  val tolerance = 0.15

  var centerAxleRadius = 2.5 + tolerance
  var shaftFlat = 0.45

  var rollerAxleRadius1 = 1.75/2 + tolerance
  var rollerAxleRadius2 = 1.0 + tolerance

  var rollerGap = 0.0
  var rollerRimGap = 0.5

  var mountThickness = 1.0

  //the rollers' dimensions
  //  innerR + maxR == radius
  //  2*math.Pi*innerR == 1/cos(angle) * nbrRollers * (rollerGap + 2*maxR)
  def maxR = {
    val c1 = 2 * Pi / nbrRollers
    val c2 = 2 / cos(angle)
    (c1 * radius - rollerGap) / (c1 + c2)
  }
  def innerR = radius - maxR
  def minR = rollerAxleRadius2 + mountThickness 
  //  width == cos(angle)*rollerHeight + 2*sin(angle) * minR + 2*cos(angle)*mountThickness
  def rollerHeight = (width - 2*sin(angle.abs) * minR - 2*cos(angle.abs)*mountThickness) / cos(angle.abs)

  def printParameters {
    Console.println("base parameters:")
    Console.println("  radius: " + radius)
    Console.println("  width: " + width)
    Console.println("  angle: " + angle)
    Console.println("  nbrRollers: " + nbrRollers)
    Console.println("  rollerAxleRadius1: " + rollerAxleRadius1)
    Console.println("  rollerAxleRadius2: " + rollerAxleRadius2)
    Console.println("  rollerGap: " + rollerGap)
    Console.println("  rollerRimGap: " + rollerRimGap)
    Console.println("  mountThickness: " + mountThickness)
    Console.println("derived parameters:")
    Console.println("  maxR: " + maxR)
    Console.println("  innerR: " + innerR)
    Console.println("  minR: " + minR)
    Console.println("  rollerHeight: " + rollerHeight)
  }

  def roller = new Roller(rollerHeight, maxR, minR, rollerAxleRadius2)

  //assumes it is centered at (0,0,0)
  protected def placeOnRim(s: Solid) = {
    val oriented = s.rotate(angle, 0, 0).translate(innerR, 0, width/2)
    val placed = for (i <- 0 until nbrRollers) yield oriented.rotate(0, 0, i * 2 * Pi / nbrRollers)
    Union(placed:_*)
  }
    
  protected def axleHeight = rollerHeight+2*mountThickness+20
  
  protected def rollersForCarving = {
    val r1 = Hull(roller.solid, roller.solid.moveX(2*maxR))
    val r2 = bigger(r1, rollerRimGap).moveZ(-rollerHeight/2)
    val c = Cylinder(rollerAxleRadius1, axleHeight).moveZ(-axleHeight/2)
    placeOnRim(r2 + c)
  }

  protected def rollers = {
    val r = roller.solid.moveZ(-rollerHeight/2)
    //val c = Cylinder(rollerAxleRadius1, axleHeight).moveZ(-axleHeight/2)
    placeOnRim(r) //+ c)
  }

  def rim = {
    val base = Tube(innerR-maxR-rollerRimGap, centerAxleRadius, width)
    val shaft = Translate(centerAxleRadius - shaftFlat, -centerAxleRadius/2, 0, Cube(2*centerAxleRadius, centerAxleRadius, width))

    val op = tan(angle.abs) * width / 2
    val ad = innerR
    val hyp = sqrt(op*op + ad*ad)
    val rth = 2*minR*sin(angle.abs) + mountThickness

    val lowerRing = Tube(hyp + rollerAxleRadius1 + mountThickness, centerAxleRadius, rth) 
    val upperRing = lowerRing.moveZ(width - rth)

    base + shaft + lowerRing + upperRing
  }

  def hub = Difference(rim, rollersForCarving)

  //the hub in two halfs, easier to print
  def hubHalves(nbrHoles: Int) = {
    val angle = Pi * 2 / nbrHoles

    val holeOffsetX = innerR / 2
    val holeOffsetA = angle / 2

    val holes = for(i <- 0 until nbrHoles) yield
        Cylinder(rollerAxleRadius1, width).moveX(holeOffsetX).rotate(0, 0, holeOffsetA + i*angle)

    val withHoles = hub -- holes
    val lowerHalf = withHoles * Cylinder(innerR + maxR, width/2)
    val upperHalf = withHoles * Cylinder(innerR + maxR, width/2).moveZ(width/2)

    val kHeight = min(5, width / 2 - 2)
    val kRadius = 1.5
    val knobX = holeOffsetX + kRadius - 1
    val knob = Cylinder(kRadius, kHeight)
    val knobs = for(i <- 0 until nbrHoles) yield knob.move(knobX, 0, width/2).rotate(0, 0, i*angle)

    val lowerWithKnobs = lowerHalf ++ knobs
    val upperWithKnobs = upperHalf -- knobs.map(bigger(_, 2*tolerance))

    (lowerWithKnobs, upperWithKnobs)
  }

  def hubHalvesPrintable(nbrHoles: Int) = {
    val (l, h) = hubHalves(nbrHoles)
    (l, h.rotate(Pi, 0, 0).moveZ(-width/2))
  }

  def assembled = Union(hub, rollers)

  def assembly = {
    import scadla.assembly._
    val rollerP = new Part("roller", roller.solid)
    val axleHeight = width / cos(angle)
    val axle = new Part("filament, 1.75mm", Cylinder(rollerAxleRadius2, axleHeight))
    axle.vitamin = true
    val (lower,upper) = hubHalves(8)
    val lowerP = new Part("hub, lower half", lower)
    val upperP = new Part("hub, upper half", upper, Some(upper.rotate(Pi, 0, 0).moveZ(-width/2)))
    val asmbl0 = Assembly("Mecanum wheel")
    def place(as: Assembly, c: Assembly, w: Vector) = {
      val jt = Joint.revolute(0,0,1)
      val f0 = Frame(Vector(innerR,0,width/2), Quaternion.mkRotation(angle, Vector(1,0,0)))
      (0 until nbrRollers).foldLeft(as)( (acc, i) => {
        val f1 = Frame(Vector(0,0,0), Quaternion.mkRotation(i * 2 * Pi / nbrRollers, Vector(0,0,1)))
        val frame = f0.compose(f1)
        acc + (frame, jt, c, w)
      })
    }
    val asmbl1 = asmbl0 +
                (Joint.fixed(0,0,-1), lowerP) +
                (Joint.fixed(0,0, 1), upperP)
    val asmbl2 = place(asmbl1, rollerP, Vector(0,0,-rollerHeight/2))
    place(asmbl2, axle, Vector(0,0,-axleHeight))
  }

}

object MecanumWheel {

  def main(args: Array[String]) {
    //a small version
    val wheel = new MecanumWheel(20, 15, Pi/6, 12)
    //val wheel = new MecanumWheel(20, 15, -Pi/6, 12)
    
    //a bigger version
    //val wheel = new MecanumWheel(30, 20, Pi/4, 12)
    //val wheel = new MecanumWheel(30, 20, -Pi/4, 12)
    
    wheel.printParameters
    
    //val obj = wheel.hub //the hub in one piece
    //val (lower, upper) = wheel.hubHalvesPrintable(8) //the hub in two half for easier printing
    //val obj = wheel.roller.solid 
    //val obj = wheel.roller.skeleton
    //val obj = wheel.roller.mold(4, 2)

    //backends.OpenSCAD.toSTL(lower, "lower1.stl") //can be directly saved as STL
    //backends.OpenSCAD.toSTL(upper, "upper1.stl")

    //the full wheel
    val obj = wheel.assembled
    backends.OpenSCAD.view(obj)
    //backends.OpenSCAD.toSTL(obj, "mechanum.stl")
    //backends.OpenSCAD.view(obj, Nil, Nil, Nil) //this version renders in a faster but with less details

    //val obj = wheel.rim
    //val obj = wheel.rollersForCarving
    //val r = backends.OpenSCAD
    //val r = new backends.ParallelRenderer(backends.OpenSCAD)
    //val r = new backends.ParallelRenderer(new backends.OpenSCAD(Nil))
    //r.toSTL(obj, "carve.stl")
  }

}