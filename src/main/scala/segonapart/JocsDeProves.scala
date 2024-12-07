package segonapart

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import mapreduce.ProcessListStrings.{getListOfFiles, llegirFitxer}
import mapreduce._
import primerapart.Main.{cosinesim, freq, nonstopfreq, paraulafreqfreq, quitstop, showfreq}
import segonapart.Main.{numPromigRefPagines, timeMeasurement}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.Source

object JocsDeProves extends App {

  val llista = List(1, 4, 10, 20, 50, 100, 4693)
  for (i <- llista) { // En aquest primer cas només funciona provar amb un reducer
    val (result, time) = timeMeasurement(numPromigRefPagines(i, 1))
    println(s"NumMappers: $i, NumReducers: 1, Time: $time")
  }

  val fitxerAlice = Source.fromFile("src/main/scala/primerapart/pg11-net.txt")
  val alice = fitxerAlice.getLines().mkString(" ") // Per afegir un espai entre línies
  fitxerAlice.close()

  val fitxerStop = Source.fromFile("src/main/scala/primerapart/english-stop.txt")
  val stop = fitxerStop.getLines().toList
  fitxerStop.close()

  val fitxerpg11 = Source.fromFile("src/main/scala/primerapart/pg11.txt")
  val pg11 = fitxerpg11.getLines().toList.mkString(" ") // Per afegir un espai entre línies
  fitxerpg11.close()

  val fitxerpg12 = Source.fromFile("src/main/scala/primerapart/pg12.txt")
  val pg12 = fitxerpg12.getLines().toList.mkString(" ") // Per afegir un espai entre línies
  fitxerpg12.close()

  var list = freq(alice)
  var list2 = freq(alice, 3)
  var list3 = nonstopfreq(alice, stop)
  var listpg11 = quitstop(pg11, stop)
  var listpg12 = quitstop(pg12, stop)

  showfreq(list)
  showfreq(list2)
  showfreq(list3)

  paraulafreqfreq(alice)

  println("Resultat de la similitud de cosinus: ( expected 1 )")
  println(cosinesim(alice, alice)) // Ha de donar 1
  println("Resultat de la similitud de cosinus: ( expected 0.8769 )")
  println(cosinesim(listpg11, listpg12)) // Ha de donar 0.8769

}