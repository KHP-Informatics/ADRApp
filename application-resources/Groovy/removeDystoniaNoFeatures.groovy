import java.util.*;
import java.io.*;
import java.lang.*;
import gate.Utils.*

annots = inputAS.get("Sentence")

if(!annots.isEmpty()) {
   for(annot in annots) {
      if(annot.getFeatures().get("Dystonia").equalsIgnoreCase("no")){
         inputAS.remove(annot);
      }
   }
}
  
