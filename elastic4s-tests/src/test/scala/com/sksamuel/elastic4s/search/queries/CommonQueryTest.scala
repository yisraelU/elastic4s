package com.sksamuel.elastic4s.search.queries

import com.sksamuel.elastic4s.{ElasticDsl, Indexable}
import com.sksamuel.elastic4s.testkit.{DiscoveryLocalNodeProvider, ElasticSugar}
import org.scalatest.{Matchers, WordSpec}

class CommonQueryTest extends WordSpec with Matchers with ElasticSugar with DiscoveryLocalNodeProvider with ElasticDsl {

  case class Condiment(name: String, desc: String)
  implicit object CondimentIndexable extends Indexable[Condiment] {
    override def json(t: Condiment): String = s""" { "name" : "${t.name}", "desc" : "${t.desc}" } """
  }

  val ketchup = Condiment("ketchup",
    "Ketchup or catsup, is a table sauce. Traditionally, different recipes featured ketchup made of mushrooms, oysters, mussels, walnuts, or other foods")
  val brownSauce = Condiment("BrownSauce",
    "Brown sauce is a traditional condiment served with food in the United Kingdom and Ireland, normally brown or dark orange in colour")
  // my american wife loves Ranch but I think it's bloody disgusting :)
  val ranch = Condiment("Ranch",
    "Ranch dressing is a type of salad dressing made of some combination of buttermilk, salt, garlic, onion, herbs")

  client.execute {
    bulk(
      indexInto("condiments" / "test") source ranch,
      indexInto("condiments" / "test") source ketchup,
      indexInto("condiments" / "test") source brownSauce
    )
  }.await

  refresh("condiments")
  blockUntilCount(3, "condiments")

  "common query" should {
    "perform query" in {
      val resp = client.execute {
        search("condiments" / "test") query {
          commonQuery("desc") text "catsup"
        }
      }.await
      resp.totalHits shouldBe 1
    }
    "use operators" in {
      val resp = client.execute {
        search("condiments" / "test") query {
          commonQuery("desc") text "buttermilk somethingnotindexed" lowFreqOperator "AND" highFreqOperator "AND"
        }
      }.await
      resp.totalHits shouldBe 0
    }
    "use lowFreqMinimumShouldMatch" in {
      val resp = client.execute {
        search("condiments" / "test") query {
          commonQuery("desc") text "buttermilk dressing salt garlic" lowFreqMinimumShouldMatch 2
        }
      }.await
      resp.totalHits shouldBe 1
    }
  }
}
