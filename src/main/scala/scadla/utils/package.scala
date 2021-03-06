package scadla

import math._

package object utils {

  import scadla._
  
  def inch2mm(i: Double) = i * 25.4

  def mm2inch(i: Double) = i / 25.4

  def polarCoordinates(x: Double, y: Double) = (math.hypot(x, y), math.atan2(y,x))
  
  def traverse(f: Solid => Unit, s: Solid): Unit = s match {
    case Translate(x, y, z, s2) =>  traverse(f, s2); f(s)
    case Rotate(x, y, z, s2) =>     traverse(f, s2); f(s)
    case Scale(x, y, z, s2) =>      traverse(f, s2); f(s)
    case Mirror(x, y, z, s2) =>     traverse(f, s2); f(s)
    case Multiply(m, s2) =>         traverse(f, s2); f(s)

    case Union(lst @ _*) =>         lst.foreach(traverse(f, _)); f(s)
    case Intersection(lst @ _*) =>  lst.foreach(traverse(f, _)); f(s)
    case Difference(s2, lst @ _*) => traverse(f, s2); lst.foreach(traverse(f, _)); f(s)
    case Minkowski(lst @ _*) =>     lst.foreach(traverse(f, _)); f(s)
    case Hull(lst @ _*) =>          lst.foreach(traverse(f, _)); f(s)

    case other => f(other)
  }
  
  def map(f: Solid => Solid, s: Solid): Solid = s match {
    case Translate(x, y, z, s2) => f(Translate(x, y, z, map(f, s2)))
    case Rotate(x, y, z, s2) => f(Rotate(x, y, z, map(f, s2)))
    case Scale(x, y, z, s2) => f(Scale(x, y, z, map(f, s2)))
    case Mirror(x, y, z, s2) => f(Mirror(x, y, z, map(f, s2)))
    case Multiply(m, s2) => f(Multiply(m, map(f, s2)))

    case Union(lst @ _*) => f(Union(lst.map(map(f, _)):_*))
    case Intersection(lst @ _*) => f(Intersection(lst.map(map(f, _)):_*))
    case Difference(s2, lst @ _*) => f(Difference(map(f, s2), lst.map(map(f, _)):_*))
    case Minkowski(lst @ _*) => f(Minkowski(lst.map(map(f, _)):_*))
    case Hull(lst @ _*) => f(Hull(lst.map(map(f, _)):_*))

    case other => f(other)
  }

  def fold[A](f: (A, Solid) => A, acc: A, s: Solid): A = s match {
    case Translate(x, y, z, s2) =>  f(fold(f, acc, s2), s)
    case Rotate(x, y, z, s2) =>     f(fold(f, acc, s2), s)
    case Scale(x, y, z, s2) =>      f(fold(f, acc, s2), s)
    case Mirror(x, y, z, s2) =>     f(fold(f, acc, s2), s)
    case Multiply(m, s2) =>         f(fold(f, acc, s2), s)

    case Union(lst @ _*) =>         f(lst.foldLeft(acc)( (acc, s2) => fold(f, acc, s2) ), s)
    case Intersection(lst @ _*) =>  f(lst.foldLeft(acc)( (acc, s2) => fold(f, acc, s2) ), s)
    case Difference(s2, lst @ _*) => f(lst.foldLeft(fold(f, acc, s2))( (acc, s3) => fold(f, acc, s3) ), s)
    case Minkowski(lst @ _*) =>    f(lst.foldLeft(acc)( (acc, s2) => fold(f, acc, s2) ), s)
    case Hull(lst @ _*) =>         f(lst.foldLeft(acc)( (acc, s2) => fold(f, acc, s2) ), s)

    case other => f(acc, other)
  }

  def simplify(s: Solid): Solid = {
    def rewrite(s: Solid): Solid = s match {
      case Cube(width, depth, height) if width <= 0.0 || depth <= 0.0 || height <= 0.0 => Empty
      case Sphere(radius) if radius <= 0.0 => Empty
      case Cylinder(radiusBot, radiusTop, height) if height <= 0.0 => Empty
      //TODO order the points/faces to get a normal form
      case Polyhedron(triangles) if triangles.isEmpty => Empty

      case Translate(x1, y1, z1, Translate(x2, y2, z2, s2)) => Translate(x1+x2, y1+y2, z1+z2, s2)
      case Rotate(x1, 0, 0, Rotate(x2, 0, 0, s2)) => Rotate(x1+x2, 0, 0, s2)
      case Rotate(0, y1, 0, Rotate(0, y2, 0, s2)) => Rotate(0, y1+y2, 0, s2)
      case Rotate(0, 0, z1, Rotate(0, 0, z2, s2)) => Rotate(0, 0, z1+z2, s2)
      case Scale(x1, y1, z1, Scale(x2, y2, z2, s2)) => Scale(x1*x2, y1*y2, z1*z2, s2)
      
      //TODO flatten ops
      case Union(lst @ _*) =>
        val lst2 = lst.toSet - Empty
        if (lst2.isEmpty) Empty else Union(lst2.toSeq: _*)
      case Intersection(lst @ _*) =>
        val lst2 = lst.toSet
        if (lst2.contains(Empty) || lst2.isEmpty) Empty else Intersection(lst2.toSeq: _*)
      case Difference(s2, lst @ _*) =>
        val lst2 = lst.toSet - Empty
        if (lst2.isEmpty) s2
        else if (lst2 contains s2) Empty
        else Difference(s2, lst2.toSeq: _*)
      case Minkowski(lst @ _*) =>
        val lst2 = lst.toSet - Empty
        if (lst2.isEmpty) Empty else Minkowski(lst2.toSeq: _*)
      case Hull(lst @ _*) =>
        val lst2 = lst.toSet - Empty
        if (lst2.isEmpty) Empty else Hull(lst2.toSeq: _*)

      case s => s
    }

    var sOld = s
    var sCurr = map(rewrite, s)
    while (sOld != sCurr) {
      sOld = sCurr
      sCurr = map(rewrite, sCurr)
    }
    sCurr
  }

  /** return a value ∈ [min,max] which is a multiple of step (or min, max) */
  def round(value: Double, min: Double, max: Double, step: Double): Double = {
    val stepped = math.rint(value / step) * step
    math.min(max, math.max(min, stepped))
  }

  /** https://en.wikipedia.org/wiki/Chord_(geometry) */
  def chord(angle: Double) = 2*sin(angle/2)

  /** https://en.wikipedia.org/wiki/Apothem */
  def apothem(nbrSides: Int, sideLength: Double) = sideLength / (2 * tan(Pi / nbrSides))
  def apothemFromR(nbrSides: Int, maxRadius: Double) = maxRadius * cos(Pi / nbrSides)

  def incribedRadius(nbrSides: Int, sideLength: Double) = sideLength / tan(Pi / nbrSides) / 2

  def circumscribedRadius(nbrSides: Int, sideLength: Double) = sideLength / sin(Pi / nbrSides) / 2

}

