import java.util.*;
import java.io.*;
import java.lang.*;
import gate.Utils.*


inputAS = doc.getAnnotations("TestKey")
annots = inputAS.get("ADE")

if(!annots.isEmpty())
    {
        for(annot in annots)
            {
                AnnotationSet contextStringAS = gate.Utils.getContainedAnnotations(inputAS, annot, "ADE")
                
                //println("Dystonia Features"+contextStringAS.firstNode().toString())
                //Iterator it3 = contextStringAS.iterator()
                
                //def fm = [:]

		//["ADE"] = "No"
                //println("I am working")    
                while(it3.hasNext())
                    {
                        Annotation coveringAnnot = it3.next();
                        if(coveringAnnot.getFeatures().get("ADE").equalsIgnoreCase("Yes"))
                            {
                                //println("Dystonia is yes")
                                fm["ADE"] = "Yes"      
                            }
			else 
			    {
                                //println("Dystonia is yes")
                                fm["ADE"] = "No"      
                            }
			
                      }
                      
                annot.setFeatures(fm.toFeatureMap())  
                
            }
       }
