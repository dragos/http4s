package org.http4s.rho.swagger

import java.sql.Timestamp
import java.util.Date

import com.wordnik.swagger.model.Model
import org.specs2.mutable.Specification

import scalaz.concurrent.Task
import scalaz.stream.Process

import scala.reflect.runtime.universe.{ TypeTag, typeTag, typeOf }

package object model {
  case class Foo(a: Int, b: String)
  type Bar = Foo
  case class FooDefault(a: Int = 0)
  case class FooGeneric[+A](a: A)
  type Foos = Seq[Foo]
  case class FooComposite(single: Foo, many: Seq[Foo])
  case class FooCompositeWithAlias(single: Bar, many: Seq[Bar], manyAlias: Foos)
  case class FooWithList(l: List[Int])
  case class FooWithMap(l: Map[String, Int])
}

class TypeBuilderSpec extends Specification {
  import model._

  def models[T](implicit t: TypeTag[T]): Set[Model] = TypeBuilder.collectModels(t.tpe, Set.empty, DefaultSwaggerFormats)

  "TypeBuilder" should {

    "Not Build a model for a primitive" in {

      val primitives = {
        Set[TypeTag[_]](typeTag[String], typeTag[Int], typeTag[Long], typeTag[Double],
          typeTag[Float], typeTag[Byte], typeTag[BigInt], typeTag[Boolean],
          typeTag[Short], typeTag[java.lang.Integer], typeTag[java.lang.Long],
          typeTag[java.lang.Double], typeTag[java.lang.Float], typeTag[BigDecimal],
          typeTag[java.lang.Byte], typeTag[java.lang.Boolean], typeTag[Number],
          typeTag[java.lang.Short], typeTag[Date], typeTag[Timestamp], typeTag[Symbol],
          typeTag[java.math.BigDecimal], typeTag[java.math.BigInteger])
      }

      val models = primitives.foldLeft(Set.empty[Model])((s, t) => TypeBuilder.collectModels(t.tpe, s, DefaultSwaggerFormats))

      models.isEmpty must_== true
    }

    "Identify types" in {
      typeOf[Task[String]].isTask should_== true
      typeOf[String].isTask should_== false

      typeOf[Process[Task,String]].isProcess should_== true
      typeOf[String].isProcess== false

      typeOf[Array[String]].isArray should_== true
      typeOf[String].isArray== false

      typeOf[Seq[String]].isCollection should_== true
      typeOf[java.util.Collection[String]].isCollection should_== true
      typeOf[String].isCollection== false

      typeOf[Either[Int,String]].isEither should_== true
      typeOf[String].isEither== false

      typeOf[Map[Int,String]].isMap should_== true
      typeOf[String].isMap== false

      typeOf[Nothing].isNothingOrNull should_== true
      typeOf[Null].isNothingOrNull should_== true
      typeOf[String].isNothingOrNull should_== false

      typeOf[Option[String]].isOption should_== true
      typeOf[String].isOption should_== false

      Reflector.primitives.foreach(_.isPrimitive should_== true)
      typeOf[Some[String]].isPrimitive should_== false
    }

    "Build simple model" in {
      val model = models[Foo].head
      model.id must_== "Foo"
      model.properties("a").`type` must_== "integer"
      model.properties("a").required must_== true
    }

    "Build a model from alias" in {
      val model = models[Bar].head
      model.id must_== "Foo"
      model.properties("a").`type` must_== "integer"
      model.properties("a").required must_== true
    }

    "Build a model with a default" in {
      val ms = models[FooDefault]
      ms.size must_== 1

      val m = ms.head

      m.name must_== "FooDefault"
      m.properties.size must_== 1
      m.properties("a").`type` must_== "integer"
      m.properties("a").required must_== false
    }

    "Build a model with a generic" in {
      val ms = models[FooGeneric[Int]]
      ms.size must_== 1
      val m = ms.head
      m.id must_== "FooGeneric«Int»"

      m.properties.size must_== 1
      m.properties("a").`type` must_== "integer"
    }

    "Build a model with a generic of type Nothing" in {
      val ms = models[FooGeneric[Nothing]]
      ms.size must_== 1
      val m = ms.head
      m.id must_== "FooGeneric«Nothing»"

      m.properties.size must_== 1
      m.properties("a").`type` must_== "void"
    }

    "Build a model with a generic of type Null" in {
      val ms = models[FooGeneric[Null]]
      ms.size must_== 1
      val m = ms.head
      m.id must_== "FooGeneric«Null»"

      m.properties.size must_== 1
      m.properties("a").`type` must_== "void"
    }

    "Build a composite model" in {
      val ms = models[FooComposite]
      ms.size must_== 2
      val m1 = ms.head
      m1.id must_== "FooComposite"
      m1.properties.size must_== 2
      m1.properties("single").`type` must_== "Foo"
      m1.properties("many").`type` must_== "List"

      val m2 = ms.tail.head
      m2.id must_== "Foo"
    }

    "Build a composite model with alias" in {
      val ms = models[FooCompositeWithAlias]
      ms.size must_== 2
      val m1 = ms.head
      m1.id must_== "FooCompositeWithAlias"
      m1.properties.size must_== 3
      m1.properties("single").`type` must_== "Foo"
      m1.properties("many").`type` must_== "List"
      m1.properties("manyAlias").`type` must_== "List"

      val m2 = ms.tail.head
      m2.id must_== "Foo"
    }

    "Build a model with a non-basic generic" in {
      val ms = models[FooGeneric[Foo]]
      ms.size must_== 2
      ms ++ models[Foo] must_== ms
    }

    "Get types from a collection" in {
      models[Seq[Foo]] must_== models[Foo]
      models[Map[String, Foo]] must_== models[Foo]
    }

    "Get types from a scalaz.stream.Process" in {
      import scalaz.concurrent.Task
      import scalaz.stream.Process
      models[Process[Task, Foo]] must_== models[Foo]
    }

    "Get types from a scalaz.concurrent.Task" in {
      import scalaz.concurrent.Task
      models[Task[Foo]] must_== models[Foo]
    }

    "Build model that contains a List" in {
      val ms = models[FooWithList]
      ms.size must_== 1
      val m = ms.head
      m.id must_== "FooWithList"

      val p = m.properties.head._2
      p.`type` must_== "List"
      p.items.isDefined must_== true

      p.items.get.`type` must_== "integer"
    }
  }

  "DataType" should {
    import org.http4s.rho.swagger.TypeBuilder.DataType

    "Get the correct name for primitives" in {
      DataType(typeTag[Int]).name must_== "integer"
      DataType(typeTag[String]).name must_== "string"
    }

    "Get the type for a container" in {
      println(DataType(typeTag[Seq[Int]]))
      DataType(typeTag[Seq[Int]]).name must_== "List"
    }
  }

}
