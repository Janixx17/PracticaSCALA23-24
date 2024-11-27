package mapreduce

import akka.actor.{Actor, ActorRef, Props}

// Les case class hauran de polimòrfiques en les "claus" i "valors" que s'utilitzen
// ja que  el MapReduce també serà polimòrfic, sinó, perdríem la genericitat.

case class MapReduceCompute()
case class toMapper[K1,V1](fitxer: K1, text: List[V1])
case class fromMapper[K2,V2](intermig: List[(K2,V2)])
case class toReducer[K2,V2](word:K2, fitxers:List[V2])
case class fromReducer[K2,V3](finals: (K2,V3))


// Els actors mappers són polimòrfics ja que  reben la funció de mapping polimòrfica que han d'aplicar
class Mapper[K1,V1,K2,V2](mapping:(K1,List[V1]) => List[(K2,V2)]) extends Actor {
  def receive: Receive = {
    // cal anotar clau:K1 i valor:List[V1] per tal d'instanciar adequadament el missatge toMapper amb les variables de tipus de Mapper
    // Compte, que no us enganyi que hagi donat els mateixos noms a les variables de tipus al Mapper que a les case class de fora. S'han
    // de vincular d'alguna manera perquè sinó l'inferidor de tipus no sap a quin dels paràmetres de tipus del mapper correspon el tipus de la clau
    // i el tipus del valor al missatge toMapper
    case toMapper(clau:K1,valor:List[V1])=>
      sender ! fromMapper(mapping(clau,valor))
  }
}

// Els actors reducers són polimòrfics ja que reben la funció de reducing polimòrfica que han d'aplicar
class Reducer[K2,V2,V3](reducing:(K2,List[V2])=> (K2,V3)) extends Actor {
  def receive: Receive = {
    // cal anotar també la clau i el valor com hem fet amb els mappers
    case toReducer(clau:K2,valor:List[V2])=>
      sender ! fromReducer(reducing(clau, valor))
  }
}



// L'Actor MapReduce és polimòrfic amb els tipus de les claus valor de l'entrada [K1,V1], la clau i valor intermitjos [k2,v2]
// i la clau i valor finals [K2,V3].
// - input és el paràmetre d'entrada (compte perquè depedent de la mida pot ser un problema)
// - mapping és la funció dels mappers
// - reducing és la funció dels reducers
class MapReduce[K1,V1,K2,V2,V3](
                                 input:List[(K1,List[V1])],
                                 mapping:(K1,List[V1]) => List[(K2,V2)],
                                 reducing:(K2,List[V2])=> (K2,V3)) extends Actor {

  var nmappers = 0 // adaptar per poder tenir menys mappers
  var mappersPendents = 0
  var nreducers = 0 // adaptar per poder tenir menys reducers
  var reducersPendents = 0

  var num_files_mapper = 0
  // dict serà el diccionari amb el resultat intermedi
  var dict: Map[K2, List[V2]] = Map[K2, List[V2]]() withDefaultValue List()
  // resultatFinal recollirà les respostes finals dels reducers
  var resultatFinal: Map[K2, V3] = Map()

  // Ens apuntem qui ens ha demanat la feina
  var client:ActorRef = null

  def receive: Receive = {

    // En rebre el missatge MapReduceCompute engeguem el procés.
    case MapReduceCompute() =>
      client = sender() // Ens apuntem qui ens ha fet l'encàrrec per enviar-li el missatge més tard.
      nmappers = input.length// farem un mapper per parella (K1,List[V1]) de l'input

      val mappers: Seq[ActorRef] = for (i <- 0 until nmappers) yield {
        context.actorOf(Props(new Mapper(mapping)), "mapper" + i)
      }

      for(((p1,p2),i)<-input.zipWithIndex) mappers(i % nmappers) ! toMapper(p1: K1, p2 :List[V1])
      mappersPendents = nmappers

    case fromMapper(list_clau_valor:List[(K2,V2)]) =>
      for ((clau, valor) <- list_clau_valor)
        dict += (clau -> (valor :: dict(clau)))

      mappersPendents -= 1

      // Quan ja hem rebut tots els missatges dels mappers:
      if (mappersPendents==0)
      {
        nreducers = dict.size // creem els reducers, tants com entrades al diccionari;

        reducersPendents = nreducers // actualitzem els reducers pendents
        val reducers = for (i <- 0 until nreducers) yield
          context.actorOf(Props(new Reducer(reducing)), "reducer"+i)
        for ((i,(key:K2, lvalue:List[V2])) <-  (0 until nreducers) zip dict)
          reducers(i) ! toReducer(key, lvalue)
        println("All sent to Reducers")
      }
    case fromReducer(entradaDictionari:(K2,V3)) =>
      resultatFinal += entradaDictionari
      reducersPendents -= 1
      if (reducersPendents == 0) {
        client ! resultatFinal
        context.stop(self)
      }
  }

}
