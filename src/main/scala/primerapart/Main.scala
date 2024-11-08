package primerapart

import scala.io.Source

object Main extends App {

  def freq(x: String, n: Int = 1): List[(String, Int)] = { // Adaptada per a que funcioni amb diverses n
    val x1 = x.toLowerCase() // Convertir a minuscules
      .replaceAll("\\W", " ") // Substituir caràcters no alfanumèrics per un espai
      .replaceAll("\\s+", " ") // Substituir espais en blanc consecutius (incloent salts de línia) per un sol espai
      .trim() // Eliminar espais en blanc al principi i al final
    val words = x1.split(" ") // Dividir el text en paraules
    val ngrams = words.sliding(n).map(_.mkString(" ")).toList // Crear els grups de n paraules
    list = ngrams.groupBy(identity).map { case (ngram, num) => (ngram, num.length) }.toList // Comptar el nombre d'ocurrències de cada ngram
    list = list.sortBy(-_._2) // Ordenar la llista pel nombre d'ocurrències
    return list
  }

  def quitstop(x: String, y: List[String]): String = {
    val x1 = x.toLowerCase() // Convertir a minuscules
      .replaceAll("\\W", " ") // Substituir caràcters no alfanumèrics per un espai
      .replaceAll("\\s+", " ") // Substituir espais en blanc consecutius (incloent salts de línia) per un sol espai
      .trim() // Eliminar espais en blanc al principi i al final
    val words = x1.split(" ") // Dividir el text en paraules
    val words2 = words.filterNot(elem => y.contains(elem)) // Eliminar les paraules de la llista de stop words
    return words2.mkString(" ") // Convertir la llista de paraules en un string
  }

  def nonstopfreq(x: String, y: List[String]): List[(String, Int)] = {
    freq(quitstop(x, y)) // Calcular la freqüència de les paraules sense les stop words
  }

  def paraulafreqfreq(x: String): Unit = {
    val x1 = freq(x)
    var freqWords = x1.groupBy(_._2).map { case (count, tuples) => (count, tuples.size) }.toList // Agrupar les paraules pel nombre d'ocurrències
    freqWords = freqWords.sortBy(-_._2) // Reordenar
    val top10 = freqWords.take(10)
    val bottom10 = freqWords.takeRight(10)
    println("10 freqüències més freqüents:")
    top10.foreach(elem => println(f"${elem._2} paraules amb ${elem._1} ocurrències"))
    println("-------------------------------------------------")
    println("10 freqüències menys freqüents:")
    bottom10.foreach(elem => println(f"${elem._2} paraules amb ${elem._1} ocurrències"))
  }

  def showfreq(x: List[(String, Int)]): Unit = {
    val totalWords = x.map(_._2).sum
    val totalDifWords = x.length
    println("Nombre total de paraules: " + totalWords)
    println("Paraules diferents: " + totalDifWords)
    println("-------------------------------------------------")
    println("Paraula    Ocurrències   Freqüència")
    x.foreach(elem => println(f"${elem._1}%-10s ${elem._2}%10d ${(elem._2 / totalWords.toFloat) * 100}%10.2f")) // Imprimir la llista correctament
  }

  def cosinesim(x: String, y: String): Double = { //Pre: x, y son strings i sense stop-words
    var x1 = freq(x).map { case (word, num) => (word, num.toDouble) } // Convertim la llista de tuples a una de tuples amb doubles
    var y1 = freq(y).map { case (word, num) => (word, num.toDouble) }
    val topx1 = x1.take(1).map(_._2).head // agafem la paraula més freqüent
    val topy1 = y1.take(1).map(_._2).head
    val x11 = x1.map { case (word, num) => (word, (num / topx1) * 100) } // Normalitzem les freqüències
    val y11 = y1.map { case (word, num) => (word, (num / topy1) * 100) }
    var x2 = x11 ++ y11.filter { case (key, _) => !x11.contains(key) }.map { case (word, _) => (word, 0.0) } // Afegim les paraules de y que no estan a x
    var y2 = y11 ++ x11.filter { case (key, _) => !y11.contains(key) }.map { case (word, _) => (word, 0.0) } // Afegim les paraules de y que no estan a x
    x2 = x2.sortBy(_._1) // Ordenem les llistes
    y2 = y2.sortBy(_._1)
    val sqrtX = math.sqrt(x2.map { case (_, num: Double) => num * num }.sum) // calculem l'arrel de la suma dels quadrats
    val sqrtY = math.sqrt(y2.map { case (_, num: Double) => num * num }.sum)
    val inferior = sqrtX * sqrtY
    val superior = x2.zip(y2).map { case ((_, numX: Double), (_, numY: Double)) => numX * numY }.sum //amb el zip combinem les llistes i fem les multiplicacions
    return superior / inferior
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

  println(cosinesim(alice, alice)) // Ha de donar 1
  println(cosinesim(listpg11, listpg12)) // Ha de donar 0.8769

}