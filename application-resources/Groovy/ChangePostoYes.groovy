//get default set
    inputAS = doc.getAnnotations("")
    originalAnnos = inputAS.get("Parkinsonian")
    
    //creates this set is if doesn't exist
    outputAS  =doc.getAnnotations("")
    outputAnnos = "Parkinsonian"    
    
    
  if(!originalAnnos.isEmpty()){
    for (anno in originalAnnos){
            fm = anno.getFeatures().toFeatureMap().clone()
	if(fm.get("Parkinsonian")!= null){
            if(fm.get("Parkinsonian").equalsIgnoreCase("yes")){
                fm.put("Parkinsonian", "positive")
            }else if (fm.get("Parkinsonian").equalsIgnoreCase("no")){
                fm.put("Parkinsonian", " unknown ")            
            }else{
                fm.put("Parkinsonian", "unknown")                
            }


            inputAS.remove(anno)
            outputAS.add(anno.start(), anno.end(), outputAnnos, fm)
  }
        }        
}
