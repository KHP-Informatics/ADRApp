import java.util.*;
import java.io.*;
import java.lang.*;
import gate.Utils.* 

 originalAnnos = inputAS.get("ManualAnnotation") 

  if(!originalAnnos.isEmpty()){
    for (anno in originalAnnos){
            fm = anno.getFeatures().toFeatureMap().clone()
        if(fm.get("observation")!= null){
            if(fm.get("observation").equalsIgnoreCase("yes")){
                fm.put("observation", "positive")
            }else if (fm.get("observation").equalsIgnoreCase("no")){
                fm.put("observation", "unknown")
            }else{
                fm.put("observation", "unknown")
            }


            inputAS.remove(anno)
            outputAS.add(anno.start(), anno.end(), "ManualAnnotation", fm)
  }
        }
}

