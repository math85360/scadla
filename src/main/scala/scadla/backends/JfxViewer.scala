package scadla.backends

import scadla._
import scadla.assembly._
import scadla.utils.box._
import dzufferey.utils.SysCmd

import scalafx.Includes._
import scalafx.application.JFXApp
import scalafx.application.JFXApp.PrimaryStage
import scalafx.beans.property.DoubleProperty
import scalafx.scene.image.Image
import scalafx.scene.input.{MouseEvent,ScrollEvent}
import scalafx.scene.paint.{Color, PhongMaterial}
import scalafx.scene.shape.{MeshView, TriangleMesh}
import scalafx.scene.transform.{Rotate,Scale,Translate}
import scalafx.scene.{AmbientLight, Group, Node, PerspectiveCamera, PointLight, Scene, SceneAntialiasing}

object JfxViewer extends Viewer {
  
  def apply(obj: Polyhedron) {
    val cmd = Array("java", "-classpath", getClassPath, "scadla.backends.JfxViewerAppP")
    val input = Some(serialize(obj))
    SysCmd.execWithoutOutput(cmd, input)
  }
  
  def apply(a: Assembly) {
    val cmd = Array("java", "-classpath", getClassPath, "scadla.backends.JfxViewerAppA")
    val input = Some(serialize(a))
    SysCmd.execWithoutOutput(cmd, input)
  }
    
  import scala.pickling.Defaults._
  import scala.pickling.json._
  //import scala.pickling.static._
  import scadla.backends.utils.Pickles._


  def serialize(obj: Polyhedron): String = {
    JfxViewerObjPoly(obj).pickle.value
  }
  
  def serialize(a: Assembly): String = {
    JfxViewerObjAssembly(a).pickle.value
  }
  
  def deserialize(str: String): JfxViewerObj = {
    //TODO
    //JSONPickle(str).unpickle[JfxViewerObj]
    JSONPickle(str).unpickle[JfxViewerObjPoly]
    //JSONPickle(str).unpickle[JfxViewerObjAssembly]
  }
  
  def deserializeP(str: String): JfxViewerObj = {
    JSONPickle(str).unpickle[JfxViewerObjPoly]
  }
  
  def deserializeA(str: String): JfxViewerObj = {
    JSONPickle(str).unpickle[JfxViewerObjAssembly]
  }


  def getClassPath = System.getProperty("java.class.path")

//for testing
//def main(args: Array[String]) {
//  val obj = OpenSCAD(Union(scadla.Translate(-2,-2,-2,Sphere(5.0)), Cube(1.0, 1.0, 1.0)))
//  apply(obj)
//}

//def main(args: Array[String]) {
//  import Assembly.part2Assembly
//  val p0 = new Part("base", scadla.Translate(-5, -5, -1, Cube(10,10,1)))
//  val p1 = new Part("center", Cylinder(3, 5))
//  val p2 = new Part("spinning", scadla.Translate(-3, -3, 0, Cube(6,6,3)))
//  val a0 = Assembly(p0)
//  val a1 = Assembly(p1)
//  val a11 = a1 + (Vector(0,0,5), Joint.revolute(0,0,1), p2)
//  val a01 = a0 + (Joint.fixed(0,0,1), a11)
//  a01.preRender(JCSG)
//  apply(a01)
//}

}

object JfxViewerAppP extends JfxViewerApp {
  override protected def deserialize(s: String) = JfxViewer.deserializeP(s)
}

object JfxViewerAppA extends JfxViewerApp {
  override protected def deserialize(s: String) = JfxViewer.deserializeA(s)
}

//TODO add parameters from the JfxViewerObj
//TODO arrows to display the axis
class JfxViewerApp extends JFXApp {

  protected def deserialize(s: String) = JfxViewer.deserialize(s)

  protected def readPolyFromStdIn = {
    val buffer = new StringBuffer
    var line = scala.io.StdIn.readLine
    while (line != null) {
      buffer.append(line)
      line = scala.io.StdIn.readLine
    }
    deserialize(buffer.toString)
  }

  stage = new PrimaryStage {
    title = "Scadla JavaFX model viewer"
    //TODO adapt resolution
    scene = new Scene(800, 600, true, SceneAntialiasing.Balanced) {
      fill = Color.LightBlue
     
      val obj = readPolyFromStdIn
      
      val box = obj.boundingBox
     
      //center at origin
      val centered = new Group(obj.mesh) {
        translateX = - box.x.center
        translateY = - box.y.center
        translateZ = - box.z.center
      }
      //scale to camera FOV
      val scaled = new Group(centered) {
        val sX = 600 / box.x.size
        val sY = 600 / box.y.size
        val sZ = 600 / box.z.size
        val s = math.min(sX, math.min(sY, sZ))
        scaleX = s
        scaleY = s
        scaleZ = s
      }
     
      // Put shapes in a group so they can be rotated together
      val shapes = new Group(scaled)
     
      val pointLight = new PointLight {
        color = Color.LightGray
        translateX = -10000.0
        translateY = -10000.0
        translateZ = -10000.0
      }
     
      val ambientLight = new AmbientLight {
        color = Color.DarkGray
      }

      root = new Group {
        children = new Group(shapes, pointLight, ambientLight)
        translateX = 400.0
        translateY = 300.0
        translateZ = 200.0
      }
     
      //TODO place the camera to get openscad-like coordinates
      camera = new PerspectiveCamera(false) {
        nearClip = 0.1
        farClip = 10000.0
      }
     
      addMouseEvents(this, shapes)
    }
  }

  /** Add mouse interaction to a scene, rotating given node. */
  def addMouseEvents(scene: Scene, node: Node) {
    val zRotate = new Rotate {
      angle = 0
      axis = Rotate.ZAxis
    }
    val angleZ = zRotate.angle
    var anchorX: Double = 0
    var anchorAngleZ: Double = 0
    
    val xRotate = new Rotate {
      angle = 90
      axis = Rotate.XAxis
    }
    val angleX = xRotate.angle
    var anchorY: Double = 0
    var anchorAngleX: Double = 0

    val scale = DoubleProperty(1)
    val scaleT = new Scale(1,1,1,0,0,0) {
      x <== scale
      y <== scale
      z <== scale
    }

    node.transforms = Seq(scaleT, xRotate, zRotate)

    scene.onMousePressed = (event: MouseEvent) => {
      anchorX = event.sceneX
      anchorY = event.sceneY
      anchorAngleZ = angleZ()
      anchorAngleX = angleX()
    }
    scene.onMouseDragged = (event: MouseEvent) => {
      angleZ() = anchorAngleZ - (anchorX - event.sceneX) / 5.0
      angleX() = anchorAngleX - (anchorY - event.sceneY) / 5.0
    }
    node.onScroll = (event: ScrollEvent) => {
      val delta = event.deltaY
      val s = scale() * (1.0 + delta / 500.0)
      //println("d = " + delta + "\ts = " + s)
      scale() = math.max(0.1, math.min(s, 4))
    }
  }

}

sealed abstract class JfxViewerObj {
  def parameters: List[(String, Double, Double)] //name, min, max
  def setParameters(params: List[Double]): Unit
  def mesh: Node
  def boundingBox: Box
}
case class JfxViewerObjPoly(p: Polyhedron) extends JfxViewerObj {
  def parameters = Nil
  def setParameters(params: List[Double]) {}
  def mesh = JfxViewerObj.objToMeshView(p)
  def boundingBox = JfxViewerObj.getBoundingBox(p)
}
case class JfxViewerObjAssembly(a: Assembly) extends JfxViewerObj {
  
  protected def setFrame( frame: Frame,
                          translate: (DoubleProperty, DoubleProperty, DoubleProperty),
                          rotate: (DoubleProperty, DoubleProperty, DoubleProperty)) = {
    val rpy = frame.orientation.toRollPitchYaw
    translate._1() = frame.translation.x
    translate._2() = frame.translation.y
    translate._3() = frame.translation.z
    rotate._1() = rpy.x
    rotate._2() = rpy.y
    rotate._3() = rpy.z
  }

  /** triple (T,R,M) where T controls the translation, R the rotation, and M is the mesh.
   *  turn the Seq[(Frame,Polyhedron)] into a series of object and tranforms (and keep refs for the update).
   */
  lazy val meshes = a.at(0).map{ case (frame, poly) =>
    val m0 = JfxViewerObj.objToMeshView(poly)
    val angleX = DoubleProperty(0)
    val angleY = DoubleProperty(0)
    val angleZ = DoubleProperty(0)
    val translate = new Translate(0,0,0)
    val translateProps = (translate.x, translate.y, translate.z)
    val rotateX = new Rotate { axis = Rotate.XAxis }
    val rotateY = new Rotate { axis = Rotate.YAxis }
    val rotateZ = new Rotate { axis = Rotate.ZAxis }
    val rotateProps = (rotateX.angle, rotateY.angle, rotateZ.angle)
    m0.transforms = Seq(rotateZ, rotateY, rotateX, translate) //TODO not sure of the order
    setFrame(frame, translateProps, rotateProps)
    (translateProps, rotateProps, m0)
  }

  def parameters = List(("time", 0, 10), ("expansion",0,1))
  def setParameters(params: List[Double]) {
    params match {
      case List(t, e) =>
        val frames = a.expandAt(e, t)
        //when updating, only the values of the tranforms needs to be updated
        meshes.zip(frames).foreach{ case ((t,r,_),(f,_)) => setFrame(f, t, r) }
      case other => sys.error("expected two parameters: " + other)
    }
  }
  def mesh = {
    val ms = meshes.map(_._3)
    new Group(ms:_*)
  }
  def boundingBox = {
    import math.{min, max}
    val bbs = a.at(0).map{ case (frame, poly) =>
      val bb = JfxViewerObj.getBoundingBox(poly)
      val vs = bb.corners.map(frame.directTo)
      BoundingBox(vs)
    }
    if (bbs.isEmpty) Box.unit
    else bbs.reduce( _ hull _ )
  }
}

object JfxViewerObj {

  def getBoundingBox(obj: Polyhedron) = {
    val b = BoundingBox(obj)
    if (b.isEmpty) Box.unit
    else b
  }

  def objToMeshView(obj: Polyhedron) = {
    val mesh = new TriangleMesh

    mesh.texCoords = Array(0.0f, 0.0f,
                           1.0f, 0.0f,
                           0.0f, 1.0f)

    val (indexedP,indexedF) = obj.indexed
    val ptsArray = Array.ofDim[Float](indexedP.size * 3)
    for ( i <- indexedP.indices ) {
      val p = indexedP(i)
      ptsArray(3*i)   = p.x.toFloat
      ptsArray(3*i+1) = p.y.toFloat
      ptsArray(3*i+2) = p.z.toFloat
    }
    mesh.points = ptsArray

    mesh.faces = indexedF.flatMap{ case (i1, i2, i3) =>
      Array(i1, 0, i2, 1, i3, 2)
    }.toArray

    mesh.faceSmoothingGroups = Array.fill(obj.faces.size)(0)
      
    new MeshView(mesh) {
      material = new PhongMaterial(Color.Gray) {
        specularColor = Color.White
      }
    }
  }

  
}
