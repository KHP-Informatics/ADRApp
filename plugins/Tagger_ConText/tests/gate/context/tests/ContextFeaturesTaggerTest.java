/**
* Copyright 2015 South London and Maudsley NHS Trust and King's College London
*
* Based on Wendy Chapman's NegEx algorithm, drawing on implementations by Imre
* Solti and Xiuyun Shen
*
* Licensed under the Apache License, Version 2.0 (the "License");
* 
* you may not use this file except in compliance with the License. You may
* obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
* WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
* License for the specific language governing permissions and limitations
* under the License.
*/



package gate.context.tests;

import gate.*;

import gate.util.persistence.PersistenceManager;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by user on 10/10/2014.
 */
public class ContextFeaturesTaggerTest {
    /**
     * GATE application for term extraction. Can be loaded in GATE IDE.
     */
    private CorpusController corpusController;

    public ContextFeaturesTaggerTest() {

    }

    /**
     * Method which init application from GATE application stored on the local drive
     */
    private void initPersistentGateResources() {
        try {
            Corpus corpus = Factory.newCorpus("New Corpus");
            corpusController = (CorpusController) PersistenceManager.loadObjectFromFile(new File("application.xgapp"));
            corpusController.setCorpus(corpus);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Method which init GATE application
     *
     * @param gatePath
     */
    public synchronized void initGateFrameWork(String gatePath) {
        try {
            if (Gate.getGateHome() == null) {
                // get GATE home directory
                File gateHome = new File(gatePath + "");
                // set parameters
                Gate.setGateHome(gateHome);
                Gate.runInSandbox(true);
                Gate.init();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * @return
     */
    public Map<String, String> processCommentText(String commentText, String concept) {
        Map<String, String> result = new HashMap<String, String>();
        gate.Document gateDocument = null;
        try {
            gateDocument = Factory.newDocument(commentText);
            corpusController.getCorpus().add(gateDocument);
            corpusController.execute();
            AnnotationSet conceptAnnotations = gateDocument.getAnnotations().get("Concept");
            if (conceptAnnotations == null || conceptAnnotations.isEmpty()) {
                result.put(ConstantsTest.NEGATION_VALUE, ConstantsTest.DEFAULT_NEGATION_VALUE);
                result.put(ConstantsTest.EXPERIENCER_VALUE, ConstantsTest.DEFAULT_EXPERIENCER_VALUE);
                result.put(ConstantsTest.TEMPORALITY_VALUE, ConstantsTest.DEFAULT_TEMPORALITY_VALUE);
                return result;
            }
            for (Annotation annotation : conceptAnnotations) {
                String conceptString = gate.Utils.stringFor(gateDocument, annotation).trim();
                if (conceptString.equalsIgnoreCase(concept)) {
                    String negated = (String) annotation.getFeatures().get(ConstantsTest.NEGATION_VALUE);
                    String temporality = (String) annotation.getFeatures().get(ConstantsTest.TEMPORALITY_VALUE);
                    String experiencer = (String) annotation.getFeatures().get(ConstantsTest.EXPERIENCER_VALUE);

                    result.put(ConstantsTest.NEGATION_VALUE, negated);
                    result.put(ConstantsTest.EXPERIENCER_VALUE, experiencer);
                    result.put(ConstantsTest.TEMPORALITY_VALUE, temporality);
                    break;
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (corpusController.getCorpus() != null) {
                corpusController.getCorpus().remove(gateDocument);
            }
            if (gateDocument != null) {
                Factory.deleteResource(gateDocument);
            }
        }
        return result;
    }

    /**
     * Generate results
     *
     * @param commentContentProcessor
     * @param pathToTestFile
     */
    public void buildReport(ContextFeaturesTaggerTest commentContentProcessor, String pathToTestFile) {
        try {
            List<String> collection = Files.readAllLines(new File(pathToTestFile).toPath(), StandardCharsets.UTF_8);
            PrintWriter printWriter = new PrintWriter("results.csv");
            for (String string : collection) {
                String[] split = string.split("\t");
                String id = split[0];
                String concept = split[1].trim();
                String sentence = split[2].trim();
                String negated = split[3];
                String temporality = split[4];
                String experiencer = split[5];
                Map<String, String> result = commentContentProcessor.processCommentText(sentence, concept);
                String negatedDetected = result.containsKey(ConstantsTest.NEGATION_VALUE) ? result.get(ConstantsTest.NEGATION_VALUE) : "?";
                String temporalityDetected = result.containsKey(ConstantsTest.TEMPORALITY_VALUE) ? result.get(ConstantsTest.TEMPORALITY_VALUE) : "?";
                String experiencerDetected = result.containsKey(ConstantsTest.EXPERIENCER_VALUE) ? result.get(ConstantsTest.EXPERIENCER_VALUE) : "?";
                boolean allTP = negated.equalsIgnoreCase(negatedDetected) && temporality.equalsIgnoreCase(temporalityDetected) && experiencer.equalsIgnoreCase(experiencerDetected);
                printWriter.println(id + "\t" + concept + "\t" + sentence + "\t" + negated + "\t" + negatedDetected + "\t" + temporality + "\t" + temporalityDetected + "\t" + experiencer + "\t" + experiencerDetected + "\t" + allTP);
            }
            printWriter.flush();
            printWriter.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void main(String[] args) {
        ContextFeaturesTaggerTest commentContentProcessor = new ContextFeaturesTaggerTest();
        commentContentProcessor.initGateFrameWork(args[0]);
        commentContentProcessor.initPersistentGateResources();
        commentContentProcessor.buildReport(commentContentProcessor, args[1]);
    }


}
