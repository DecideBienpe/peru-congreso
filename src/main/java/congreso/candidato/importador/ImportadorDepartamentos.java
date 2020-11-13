package congreso.candidato.importador;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;

public class ImportadorDepartamentos {

  public static void main(String[] args) throws IOException {
    final var baseUrl = "http://peruvotoinformado.com/public";
    var doc = Jsoup.connect(baseUrl + "/2020").get();
    var listaDepartamentos = doc.body().select("li.list-group-item");
    var map =
        listaDepartamentos
            .stream()
            .map(item -> item.select("a"))
            .collect(Collectors.toMap(a -> a.text().trim(), a -> a.attr("href").trim()));

    var refCongresistaPorRegion = new LinkedHashMap<>();

    map.forEach((region, href) -> {
      try {
        var docRegion = Jsoup.connect(baseUrl + href).get();
//        Files.writeString(Path.of("target/candidatos/departamentos/" + region + ".html"),
//            docRegion.toString());
        var cards = docRegion.select("div.card-body");
        var refCandidatos = cards
            .stream()
            .map(c -> c.select("a").attr("href"))
            .collect(Collectors.toList());
        refCongresistaPorRegion.put(region, refCandidatos);
      } catch (IOException e) {
        e.printStackTrace();
      }
    });


  }

}
