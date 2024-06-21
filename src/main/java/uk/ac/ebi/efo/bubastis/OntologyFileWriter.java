package uk.ac.ebi.efo.bubastis;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: James Malone
 * Date: 28/11/13
 * Time: 12:07
 * To change this template use File | Settings | File Templates.
 */
public class OntologyFileWriter {

    String filePath;


    //default constructor
    public OntologyFileWriter() {

    }


    //constructor
    public OntologyFileWriter(String filePath) {
        this.filePath = filePath;
    }


    public void writeDiffToFile(String filePath, OntologyChangesBean changeBean) throws IOException {


        //open stream to write to
        File file = new File(filePath);
        FileWriter fstream = new FileWriter(file, false);

        //write xml header
        this.writeHeader(fstream, changeBean);

        //write changed classes as xml
        ArrayList<OWLClassAxiomsInfo> changedClasses = changeBean.getClassesWithDifferences();
        this.writeListToFile(fstream, changedClasses, "@Classes modified from previous");

        //write new classes as xml
        ArrayList<OWLClassAxiomsInfo> newClasses = changeBean.getNewClasses();
        this.writeListToFile(fstream, newClasses, "@Classes new to this version");

        //write deleted classes as xml
        ArrayList<OWLClassAxiomsInfo> deletedClasses = changeBean.getDeletedClasses();
        this.writeListToFile(fstream, deletedClasses, "@Classes deleted from this version");

        //Close the output stream
        fstream.close();


    }

    private void writeListToFile(FileWriter fstream, ArrayList<OWLClassAxiomsInfo> classesWithDifferences, String header) throws IOException {

        if (!classesWithDifferences.isEmpty()) {
            fstream.write("\n" + "################" + "\n" + header + "\n\n");

            //iterate through and display to screen
            Iterator<OWLClassAxiomsInfo> i = classesWithDifferences.iterator();
            while (i.hasNext()) {
                OWLClassAxiomsInfo classChangeInfo = i.next();

                fstream.write("Class: " + classChangeInfo.getIRI().toString() + "\n");
                fstream.write("Label(s): ");
                Set<String> labels = classChangeInfo.getLabelsAsString();
                for (String label : labels) {
                    fstream.write(label + " ");

                }
                fstream.write("\n");

                if (classChangeInfo.getDeletedClassAxiomsAsLabels() != null) {
                    for (String axiom : classChangeInfo.getDeletedClassAxiomsAsLabels()) {
                        fstream.write("- " + axiom + "\n");
                    }
                }
                if (classChangeInfo.getNewClassAxiomsAsLabels() != null){
                    for (String axiom : classChangeInfo.getNewClassAxiomsAsLabels()) {
                        fstream.write("+ " + axiom + "\n");
                    }
                }

                fstream.write("\n");
            }
        }


    }


    public void writeHeader(FileWriter fs, OntologyChangesBean changeBean) throws IOException {

        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Date date = new Date();

        fs.write("Ontology Diff Summary" + "\n");
        fs.write("Diff date: " + dateFormat.format(date));
        fs.write("\n");
        fs.write("First ontology URI: " + changeBean.getOntology1Location() + "\n" +
                "Second ontology URI: " + changeBean.getOntology2Location() + "\n");
        fs.write("Number of classes changed: " + changeBean.getNumChangedClasses() + "\n");
        fs.write("Number of classes added: " + changeBean.getNumNewClasses() + "\n");
        fs.write("Number of classes deleted: " + changeBean.getNumDeletedClasses() + "\n \n");
        
        int newAxiomCount = 0;
        int deletedAxiomCount = 0;
        int newAnnotationCount = 0;
        int deletedAnnotationCount = 0;
        
        for (OWLClassAxiomsInfo classInfo : changeBean.getClassesWithDifferences()) {
            newAnnotationCount += Optional.ofNullable(classInfo.getNewRawAnnotations()).orElse(Collections.emptySet()).size();
            deletedAnnotationCount += Optional.ofNullable(classInfo.getDeletedRawAnnotations()).orElse(Collections.emptySet()).size();
            newAxiomCount += Optional.ofNullable(classInfo.getNewAxioms()).orElse(Collections.emptySet()).size();
            deletedAxiomCount += Optional.ofNullable(classInfo.getDeletedAxioms()).orElse(Collections.emptySet()).size();
        }
        
        fs.write("Number of class axioms added: " + newAxiomCount + "\n \n");
        fs.write("Number of class axioms deleted: " + deletedAxiomCount + "\n \n");
        fs.write("Number of annotations added: " + newAnnotationCount + "\n \n");
        fs.write("Number of annotations deleted: " + deletedAnnotationCount + "\n \n");
        
    }

}
