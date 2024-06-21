package care.smith.top.terminology.codes.versioning;

import org.eclipse.rdf4j.model.BNode;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.util.ModelBuilder;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.Rio;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import uk.ac.ebi.efo.bubastis.OntologyChangesBean;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;

/**
 * Renders an ontology change set as an RDF (named) graph using the TOP Ontology Versioning Ontology as the schema.
 *
 * @author Ralph Schäfermeier
 */
public class TopVersionOntologyDiffRDFRenderer {
  
  private static final SimpleValueFactory valueFactory = SimpleValueFactory.getInstance();
  
  private static final String NS_CHANGES = "https://top.smith.care/terminology/changes#";
  
  private static final IRI PROPERTY_HASCHANGE = SimpleValueFactory.getInstance().createIRI(NS_CHANGES, "hasChange");
  private static final IRI PROPERTY_NEWTERM = SimpleValueFactory.getInstance().createIRI(NS_CHANGES, "newTerm");
  private static final IRI PROPERTY_OLDTERM = SimpleValueFactory.getInstance().createIRI(NS_CHANGES, "oldTerm");
  private static final IRI PROPERTY_NEWLABEL = SimpleValueFactory.getInstance().createIRI(NS_CHANGES, "newLabel");
  private static final IRI PROPERTY_OLDLABEL = SimpleValueFactory.getInstance().createIRI(NS_CHANGES, "oldLabel");
  private static final IRI PROPERTY_ANNOATIONPROPERTY = SimpleValueFactory.getInstance().createIRI(NS_CHANGES, "annotationProperty");
  private static final IRI PROPERTY_ANNOATIONVALUE = SimpleValueFactory.getInstance().createIRI(NS_CHANGES, "annotationValue");
  private static final IRI PROPERTY_ANNOATIONDATATYPE = SimpleValueFactory.getInstance().createIRI(NS_CHANGES, "annotationDatatype");
  
  public void writeDiffToFile(String filePath, OntologyChangesBean changeBean, RDFFormat format) throws IOException {
    ModelBuilder mob = new ModelBuilder();
    mob.
            setNamespace("v", NS_CHANGES).
            setNamespace("o1", changeBean.getOntology1().getOntologyID().getVersionIRI().get().toString() + "#").
            setNamespace("o2", changeBean.getOntology2().getOntologyID().getVersionIRI().get().toString() + "#").
            defaultGraph().add("o1:", "v:hasSuccessorVersion", "o2:").
            namedGraph("o2:");
    
    Optional.ofNullable(changeBean.getNewClasses()).orElse(new ArrayList<>()).forEach(owlClassAxiomsInfo -> {
      BNode changeInstance = valueFactory.createBNode();
      mob.
              add(valueFactory.createIRI(owlClassAxiomsInfo.getIRIAsString()), PROPERTY_HASCHANGE, changeInstance).
              add(changeInstance, RDF.TYPE, "v:Addition");
    });
    
    Optional.ofNullable(changeBean.getDeletedClasses()).orElse(new ArrayList<>()).forEach(owlClassAxiomsInfo -> {
      BNode changeInstance = valueFactory.createBNode();
      mob.
              add(valueFactory.createIRI(owlClassAxiomsInfo.getIRIAsString()), PROPERTY_HASCHANGE, changeInstance).
              add(changeInstance, RDF.TYPE, "v:Deletion");
    });
    
    Optional.ofNullable(changeBean.getClassesWithDifferences()).orElse(new ArrayList<>()).forEach(owlClassAxiomsInfo -> {
      
      IRI currentClassIRI = valueFactory.createIRI(owlClassAxiomsInfo.getIRIAsString());
      
      Optional.ofNullable(owlClassAxiomsInfo.getNewAxioms()).orElse(Collections.emptySet()).forEach(axiom -> {
        if (axiom instanceof OWLSubClassOfAxiom) {
          OWLSubClassOfAxiom scoa = (OWLSubClassOfAxiom) axiom;
          if (scoa.getSuperClass().asOWLClass().getIRI().equals(owlClassAxiomsInfo.getIRI())) {
            // current class is super class in axiom, so it has got a new subclass
            BNode changeInstance = valueFactory.createBNode();
            mob.
                    add(currentClassIRI, PROPERTY_HASCHANGE, changeInstance).
                    subject(changeInstance).
                    add(RDF.TYPE, "v:SubTermAddition").
                    add(PROPERTY_NEWTERM, valueFactory.createIRI(scoa.getSuperClass().asOWLClass().getIRI().toString()));
          } else if (scoa.getSubClass().asOWLClass().getIRI().equals(owlClassAxiomsInfo.getIRI())) {
            // current class is subclass in axiom, so it has got a new super class
            BNode changeInstance = valueFactory.createBNode();
            mob.
                    add(currentClassIRI, PROPERTY_HASCHANGE, changeInstance).
                    subject(changeInstance).
                    add(RDF.TYPE, "v:SuperTermAddition").
                    add(PROPERTY_NEWTERM, valueFactory.createIRI(scoa.getSubClass().asOWLClass().getIRI().toString()));
          } else {
            System.err.println("Warning: class " + currentClassIRI.getLocalName() + " does not appear in axiom but should: " + axiom);
          }
        } else if (axiom instanceof OWLEquivalentClassesAxiom) {
          OWLEquivalentClassesAxiom eca = (OWLEquivalentClassesAxiom) axiom;
          eca.getNamedClasses().stream().filter(clazz -> !(clazz.getIRI().toString().equals(currentClassIRI.toString()))).forEach(clazz -> {
            BNode changeInstance = valueFactory.createBNode();
            mob.
                    add(currentClassIRI, PROPERTY_HASCHANGE, changeInstance).
                    subject(changeInstance).
                    add(RDF.TYPE, "v:EquivalenceAddition").
                    add(PROPERTY_NEWTERM, valueFactory.createIRI(clazz.getIRI().toString()));
          });
        }
      });
      
      Optional.ofNullable(owlClassAxiomsInfo.getNewRawAnnotations()).orElse(Collections.emptySet()).forEach(annotation -> {
        BNode changeInstance = valueFactory.createBNode();
        mob.
                add(currentClassIRI, PROPERTY_HASCHANGE, changeInstance).
                subject(changeInstance).
                add(RDF.TYPE, "v:LabelAddition").
                add(PROPERTY_ANNOATIONPROPERTY, valueFactory.createIRI(annotation.getProperty().getIRI().toString()));
        
        OWLLiteral annotationValue = annotation.getValue().asLiteral().get();
        String literal = annotationValue.getLiteral();
        Literal value = annotationValue.hasLang() ? valueFactory.createLiteral(literal, annotationValue.getLang()) : valueFactory.createLiteral(literal, valueFactory.createIRI(annotationValue.getDatatype().getIRI().toString()));
        mob.add(PROPERTY_ANNOATIONVALUE, value);
      });
      
      Optional.ofNullable(owlClassAxiomsInfo.getDeletedAxioms()).orElse(Collections.emptySet()).forEach(axiom -> {
        if (axiom instanceof OWLSubClassOfAxiom) {
          OWLSubClassOfAxiom scoa = (OWLSubClassOfAxiom) axiom;
          if (scoa.getSuperClass().asOWLClass().getIRI().equals(owlClassAxiomsInfo.getIRI())) {
            // current class is super class in axiom, so it has got a subclass removed
            BNode changeInstance = valueFactory.createBNode();
            mob.
                    add(currentClassIRI, PROPERTY_HASCHANGE, changeInstance).
                    subject(changeInstance).
                    add(RDF.TYPE, "v:SubTermDeletion").
                    add(PROPERTY_OLDTERM, valueFactory.createIRI(scoa.getSuperClass().asOWLClass().getIRI().toString()));
          } else if (scoa.getSubClass().asOWLClass().getIRI().equals(owlClassAxiomsInfo.getIRI())) {
            // current class is subclass in axiom, so it has got a super class removed
            BNode changeInstance = valueFactory.createBNode();
            mob.
                    add(currentClassIRI, PROPERTY_HASCHANGE, changeInstance).
                    subject(changeInstance).
                    add(RDF.TYPE, "v:SuperTermDeletion").
                    add(PROPERTY_OLDTERM, valueFactory.createIRI(scoa.getSubClass().asOWLClass().getIRI().toString()));
          } else {
            System.err.println("Warning: class " + currentClassIRI.getLocalName() + " does not appear in axiom but should: " + axiom);
          }
        } else if (axiom instanceof OWLEquivalentClassesAxiom) {
          OWLEquivalentClassesAxiom eca = (OWLEquivalentClassesAxiom) axiom;
          eca.getNamedClasses().stream().filter(clazz -> !(clazz.getIRI().toString().equals(currentClassIRI.toString()))).forEach(clazz -> {
            BNode changeInstance = valueFactory.createBNode();
            mob.
                    add(currentClassIRI, PROPERTY_HASCHANGE, changeInstance).
                    subject(changeInstance).
                    add(RDF.TYPE, "v:EquivalenceDeletion").
                    add(PROPERTY_OLDTERM, valueFactory.createIRI(clazz.getIRI().toString()));
          });
        }
      });
      
      Optional.ofNullable(owlClassAxiomsInfo.getDeletedRawAnnotations()).orElse(Collections.emptySet()).forEach(annotation -> {
        BNode changeInstance = valueFactory.createBNode();
        mob.
                add(currentClassIRI, PROPERTY_HASCHANGE, changeInstance).
                subject(changeInstance).
                add(RDF.TYPE, "v:LabelDeletion").
                add(PROPERTY_ANNOATIONPROPERTY, valueFactory.createIRI(annotation.getProperty().getIRI().toString()));
        
        OWLLiteral annotationValue = annotation.getValue().asLiteral().get();
        String literal = annotationValue.getLiteral();
        Literal value = annotationValue.hasLang() ? valueFactory.createLiteral(literal, annotationValue.getLang()) : valueFactory.createLiteral(literal, valueFactory.createIRI(annotationValue.getDatatype().getIRI().toString()));
        mob.add(PROPERTY_ANNOATIONVALUE, value);
      });
    });
    
    Model rdfModel = mob.build();
    FileOutputStream out = new FileOutputStream(filePath);
    try {
      Rio.write(rdfModel, out, format);
    } catch (RDFHandlerException ex) {
      throw new IOException(ex);
    } finally {
      out.close();
    }
  }
}
