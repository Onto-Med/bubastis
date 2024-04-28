package care.smith.top.terminology.codes.versioning;

import org.eclipse.rdf4j.rio.RDFFormat;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;
import org.semanticweb.owlapi.vocab.SKOSVocabulary;
import uk.ac.ebi.efo.bubastis.CompareOntologies;
import uk.ac.ebi.efo.bubastis.exceptions.Ontology1LoadException;
import uk.ac.ebi.efo.bubastis.exceptions.Ontology2LoadException;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Ralph Schäfermeier
 */
public class BubastisRunner {
  
  private static final List<IRI> annotationProperties = Stream.of(
          CompareOntologies.CODE_IRI,
          OWLRDFVocabulary.RDFS_LABEL.getIRI(),
          SKOSVocabulary.PREFLABEL.getIRI(),
          SKOSVocabulary.ALTLABEL.getIRI()
  ).collect(Collectors.toList());
  
  public static void main(String[] args) {
    File baseDir = new File(args[0]);
    
    List<File> files = Arrays.stream(baseDir.listFiles(file -> file.isFile() && file.getName().startsWith("test-") && file.getName().endsWith(".owl"))).sorted().collect(Collectors.toList());
    
    File older = files.remove(0);
    while(!files.isEmpty()) {
      File newer = files.remove(0);
      System.out.println(older.getName() + "-" + newer.getName());
      try {
        CompareOntologies bubastis = new CompareOntologies();
        bubastis.doFindAllChanges(older, newer, annotationProperties);
        bubastis.writeDiffAsRDFFile(new File(baseDir, older.getName() + "-" + newer.getName() + ".trig").getAbsolutePath(), RDFFormat.TRIG);
        older = newer;
      } catch (Ontology1LoadException e) {
        throw new RuntimeException(e);
      } catch (Ontology2LoadException e) {
        throw new RuntimeException(e);
      }
    }
  }
  
}
