import java.util.*;

import java.io.*;

import java.lang.*;

import gate.Utils.*




 originalAnnos = inputAS.get("ManualAnnotation")




  if(!originalAnnos.isEmpty()){

    for (anno in originalAnnos){

            fm = anno.getFeatures().toFeatureMap().clone()

        if(fm.get("Parkinsonian")!= null){

            if(fm.get("Parkinsonian").equalsIgnoreCase("yes")){

                fm.put("Parkinsonian", "positive")

            }else if (fm.get("Parkinsonian").equalsIgnoreCase("no")){

                fm.put("Parkinsonian", "unknown")

            }else{

                fm.put("Parkinsonian", "unknown")

            }



            inputAS.remove(anno)

            outputAS.add(anno.start(), anno.end(), "ManualAnnotation", fm)

  }

        }

}

