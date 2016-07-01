import java.util.*;
import java.io.*;
import java.lang.*;
import gate.Utils.*

annots = inputAS.get("TardiveDyskinesia")

if(!annots.isEmpty()) {
   for(annot in annots) {
      if(annot.getFeatures().get("TardiveDyskinesia").equalsIgnoreCase("no")){

         inputAS.remove(annot);
      }
   }
}
  
