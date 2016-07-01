import java.util.*;
import java.io.*;
import java.lang.*;
import gate.Utils.*

annots = inputAS.get("ADR")

if(!annots.isEmpty()) 
     {
         for(annot in annots) 
               {
                    FeatureMap fm = annot.getFeatures();
                    if((fm.get("Experiencer").equalsIgnoreCase("Patient")) && (fm.get("Negation").equalsIgnoreCase("Affirmed")) && (fm.get("Temporality").equalsIgnoreCase("Recent")))
                        {
                            fm.put("ADE_Status","Yes")
                            
                        }  
                 /* else if((fm.get("Experiencer").equalsIgnoreCase("Patient")) && (fm.get("Negation").equalsIgnoreCase("Affirmed")) && (fm.get("Temporality").equalsIgnoreCase("Hypothetical")))
                        {
                           
                            fm.put("ADE_Status","Yes")
                             println("Hypothetical with negation as Affirmed")
                             println(doc.getName())
                        }   */
                     else 
                        {
                            fm.put("ADE_Status","No")
                        }
                }    
    }  