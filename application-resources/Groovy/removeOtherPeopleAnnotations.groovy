import java.util.*;
import java.io.*;
import java.lang.*;
import gate.Utils.*

annots = inputAS.get("Sentence")

if(!annots.isEmpty()) {
   for(annot in annots) {
      if(annot.getFeatures().get("Dystonia").equalsIgnoreCase("yes")){
         AnnotationSet negset = gate.Utils.getContainedAnnotations(inputAS,annot,"Person")
           if(negset.iterator().hasNext()){ 
             fm = annot.getFeatures()
             fm.put("Dystonia","No")
             annot.setFeatures(fm)
           }
     }
   }
}
  
