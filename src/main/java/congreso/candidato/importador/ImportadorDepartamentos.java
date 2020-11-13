package congreso.candidato.importador;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;

public class ImportadorDepartamentos {

  public static void main(String[] args) throws IOException {
    final var baseUrl = "http://peruvotoinformado.com/public";
    var doc = Jsoup.connect(baseUrl + "/2020").get();
    var listaDepartamentos = doc.body().select("li.list-group-item");
    var refRegiones =
        listaDepartamentos
            .stream()
            .map(item -> item.select("a"))
            .collect(Collectors.toMap(a -> a.text().trim(), a -> a.attr("href").trim()));

    var refCongresistaPorRegion = new LinkedHashMap<String, List<String>>();

    refRegiones.forEach((region, href) -> {
      try {
//        Files.writeString(Path.of("target/candidatos/departamentos/" + region + ".html"),
//            docRegion.toString());
        int i = 0;
        int page = 1;
        do {
          var docRegion = Jsoup.connect(baseUrl + href + "?page=" + page).get();
          var cards = docRegion.select("div.card-body");
          i = cards.size();
          var refCandidatos = cards
              .stream()
              .map(c -> c.select("a").attr("href"))
              .collect(Collectors.toList());
          var list = refCongresistaPorRegion.getOrDefault(region, new ArrayList<>());
          list.addAll(refCandidatos);
          refCongresistaPorRegion.put(region, list);
          System.out.println(region + ": candidatos obtenidos " + list.size());
          page ++;
        } while (i > 0);
      } catch (IOException e) {
        e.printStackTrace();
      }
    });

    refCongresistaPorRegion.forEach((region, refs) -> {
      var buffer = new StringBuffer();
      try {
        refs.forEach(r -> buffer.append(String.format("%s%n", r)));
        Files.writeString(
            Path.of("static/regiones/" + region.replace(" ", "-") + ".txt"),
            buffer.toString());
      } catch (IOException e) {
        e.printStackTrace();
      }
    });
  }

}
