import java.util.*;
import java.io.*;
import java.lang.*;
import gate.Utils.*


//inputAS = doc.getAnnotations()
annots = inputAS.get("Sentence")

if(!annots.isEmpty())
    {
        for(annot in annots)
            {
                AnnotationSet contextStringAS = gate.Utils.getContainedAnnotations(inputAS, annot, "Dystonia")
                
                //println("Dystonia Features"+contextStringAS.firstNode().toString())
                Iterator it3 = contextStringAS.iterator()
                
                def fm = [:]

                fm["Dystonia"] = "No"
                //println("I am working")    
                while(it3.hasNext())
                    {
                        Annotation coveringAnnot = it3.next();
                        if(coveringAnnot.getFeatures().get("Dystonia").equalsIgnoreCase("Yes"))
                            {
                                //println("Dystonia is yes")
                                fm["Dystonia"] = "Yes"      
                            }
                      }
                      
                annot.setFeatures(fm.toFeatureMap())  
                
            }
       }
       
