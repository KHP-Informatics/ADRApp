//get default set
//inputAS = doc.getAnnotations("Key")
originalAnnos = inputAS.get("ManualAnnotation")

//creates this set is if doesn't exist
//outputAS  =doc.getAnnotations("TestKey")
outputAnnos = "TardiveDyskinesia"   
  
for (anno in originalAnnos){
  fm = anno.getFeatures().toFeatureMap().clone()
  fm.put("TardiveDyskinesia", anno.getFeatures().get("observation"))
  fm.put("Type", anno.getFeatures().get("Type")) 
  outputAS.add(anno.start(), anno.end(), outputAnnos, fm)
}
