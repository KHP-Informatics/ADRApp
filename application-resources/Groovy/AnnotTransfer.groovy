import java.util.*;
import java.io.*;
import java.lang.*;
import gate.Utils.*


//inputAS = doc.getAnnotations("Key")

annots = doc.getAnnotations("Key").get("ManualAnnotation") 
if(!annots.isEmpty())
    {
        for(annot in annots)
            {
                inputAS.put(annot.start(), annot.end(),"ADR", annot.getFeatures().clone())
            }
       }
     
