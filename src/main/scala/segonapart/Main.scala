package segonapart

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Await

import scala.io.Source
import mapreduce.ViquipediaParse
import main.tractaxml
import mapreduce.ProcessListStrings.getListOfFiles
import mapreduce._

object Main extends App {

  class Reducer[K2, V2, V3](reducing: (K2, List[V2]) => (K2, V3)) extends Actor {
    def receive: Receive = {
      case toReducer(clau: K2, valor: List[V2]) =>
        if (valor.nonEmpty) {
          sender ! fromReducer(reducing(clau, valor))
        } else {
          println(s"Reducer amb clau $clau no té valors a processar.")
        }
    }
  }

  class MR[K1,V1,K2,V2,V3](
                                   input:List[(K1,List[V1])],
                                   mapping:(K1,List[V1]) => List[(K2,V2)],
                                   reducing:(K2,List[V2])=> (K2,V3),
                                   numMapers: Int = 0,
                                   numReducers: Int = 0) extends Actor {


    var nmappers = numMapers
    var mappersPendents = 0
    var nreducers = numReducers
    var reducersPendents = 0

    var num_files_mapper = 0
    var dict: Map[K2, List[V2]] = Map[K2, List[V2]]() withDefaultValue List()
    // resultatFinal recollirà les respostes finals dels reducers
    var resultatFinal: Map[K2, V3] = Map()

    // Ens apuntem qui ens ha demanat la feina
    var client:ActorRef = null


    def receive: Receive = {

      // En rebre el missatge MapReduceCompute engeguem el procés.
      case MapReduceCompute() =>
        println("Hem rebut lencarrec")
        client = sender() // Ens apuntem qui ens ha fet l'encàrrec per enviar-li el missatge més tard.

        // farem un mapper per parella (K1,List[V1]) de l'input
        if (nmappers == 0){
          nmappers = input.length
        }
        else {
          nmappers = numMapers
        }
        println("nmappers: " + nmappers)

        println("Going to create MAPPERS!!")

        // Al crear actors a dins d'altres actors, enlloc de crear el ActorSystem utilitzarem context i així es va
        // organitzant la jerarquia d'actors.
        // D'altra banda, quan els actors que creem tenen un contructor amb paràmetre, no passem el "tipus" de l'actor i prou
        // a Props sino que creem l'actor amb els paràmetres que necessita. En aquest cas, l'Actor mapping és paramètric en tipus
        // i necessita com a paràmetre una funció de mapping.

        val mappers: Seq[ActorRef] = for (i <- 0 until nmappers) yield {
          context.actorOf(Props(new Mapper(mapping)), "mapper" + i)
        }
        // No és necessari passar els tipus K1,V1, ... ja que els infereix SCALA pel paràmetre mapping
        // D'altra banda compte pq  "0 until n" és el mateix que "0 to n-1".
        // val mappers = for (i <- 0 to nmappers-1) yield {
        //      context.actorOf(Props(new Mapper[K1,V1,K2,V2](mapping)), "mapper"+i)
        // }

        // Posar les anotacions de tipus aquí no és necessari però ajuda a llegir que
        // a cada mapper li enviem una clau de tipus K1 i una llista de valors de tipus V1

        // Aquesta versi és quadràtica perquè l'accés a input(i) te cost i mentre
        // que la versió de sota amb el zipWithIndex senzillament recorre l'input un cop
        // linealment. AQUEST CANVI ÉS CLAU EN EL RENDIMENT
        //for(i<- 0 until nmappers) mappers(i) ! toMapper(input(i)._1:K1, input(i)._2: List[V1])

        for(((p1,p2),i)<-input.zipWithIndex) mappers(i % nmappers) ! toMapper(p1: K1, p2 :List[V1])

        // Per tant, alternativament...
        // for(i<- 0 until nmappers) mappers(i) ! toMapper(input(i)._1, input(i)._2)

        // Necessitem controlar quant s'han acabat tots els mappers per poder llençar els reducers després...
        mappersPendents = nmappers

        println("All sent to Mappers, now start listening...")


      // Anem rebent les respostes dels mappers i les agrupem al diccionari per clau.
      // Tornem a necessitar anotar els tipus del paràmetre que reb fromMapper tal com ho hem fet
      // o be en el codi comentat al generar les tuples.

      case fromMapper(list_clau_valor:List[(K2,V2)]) =>
        for ((clau, valor) <- list_clau_valor)
          dict += (clau -> (valor :: dict(clau)))

        mappersPendents -= 1

        // Quan ja hem rebut tots els missatges dels mappers:
        if (mappersPendents==0)
        {
          // creem els reducers, tants com entrades al diccionari; fixeu-vos de nou que fem servir context i fem el new
          // pel constructor del Reducer amb paràmetres
          if (nreducers == 0) {
            nreducers = dict.size
          }
          else {
            nreducers = numReducers
          }

          println("nreducers: " + nreducers)
          reducersPendents = Math.min(nreducers, dict.size) // actualitzem els reducers pendents
          val reducers = for (i <- 0 until Math.min(nreducers, dict.size)) yield //es creen tants reducers com entrades al diccionari
            context.actorOf(Props(new Reducer(reducing)), s"reducer$i")

          // No cal anotar els tipus ja que els infereix de la funció reducing
          //context.actorOf(Props(new Reducer[K2,V2,V3](reducing)), "reducer"+i)

          // Ara enviem a cada reducer una clau de tipus V2 i una llista de valors de tipus K2. Les anotacions de tipus
          // no caldrien perquè ja sabem de quin tipus és dict, però ens ajuden a documentar.
          for ((i,(key:K2, lvalue:List[V2])) <-  (0 until nreducers) zip dict)
            reducers(i) ! toReducer(key, lvalue)
          println("All sent to Reducers")
        }

      // A mesura que anem rebent respostes del reducer, tuples (K2, V3), les anem afegint al Map del resultatfinal i
      // descomptem reducers pendents. Tornem a necessitar anotar el tipus.
      case fromReducer(entradaDictionari:(K2,V3)) =>
        resultatFinal += entradaDictionari
        reducersPendents -= 1


        // En arribar a 0 enviem a qui ens ha encarregat el MapReduce el resultat. De fet l'està esperant pq haurà fet un ask.
        if (reducersPendents == 0) {
          client ! resultatFinal
          println("All Done from Reducers!")
          // Ara podem alliberar els recursos dels actors, el propi MapReduce, tots els mappers i els reducers.
          // Fixem-nos que no te sentit que tornem a engegar "aquest mateix" MapReduce.
          context.stop(self)
        }
    }
  }

  def numPromigRefPagines(numMapers: Int = 0, numReducers: Int = 0): Double = {
    val actorSystem = ActorSystem("reducer-system")
    val files = getListOfFiles("viqui_files") // nombres de archivos
    val numfiles = files.length // cantidad de archivos
    val nFilesMapper = Math.ceil(numfiles.toDouble / numMapers).toInt // funcio mes complexa per asegurar una bona divisio


    val input = files.grouped(nFilesMapper).zipWithIndex.map {
      case (fileGroup, index) => (index, fileGroup)
    }.toList

    val mapping = (key: Int, fileList: List[java.io.File]) => {
      val totalRefs = fileList.map(file => ViquipediaParse.parseViquipediaFile(file.toString).refs.length).sum
      List(("total", totalRefs))
    }

    val reducing = (key: String, values: List[Int]) => {
      val totalReferences = values.sum
      (key, totalReferences)
    }

    val mapReduce = actorSystem.actorOf(Props(new MR(input, mapping, reducing, numMapers, numReducers)), "mapReduce")

    implicit val timeout: Timeout = Timeout(10000.seconds)
    val futureResult = mapReduce ? MapReduceCompute()
    Await.result(futureResult, timeout.duration)
    val resultat = futureResult.value.get.get.asInstanceOf[Map[String, Int]].get("total").get.toDouble / numfiles
    resultat
  }

  def timeMeasurement[A](function: => A): (A, Double) = {
    val startTime = System.nanoTime()
    val result = function
    val endTime = System.nanoTime()
    val time = (endTime - startTime) / 1e9 //ho convertim a segon amb 1e9
    (result, time)
  }



  for (i <- 1 to 20) { // En aquest primer cas només funciona provar amb un reducer
      val (result, time) = timeMeasurement(numPromigRefPagines(i, 1))
      println(s"NumMappers: $i, NumReducers: 1, Time: $time")
  }
}