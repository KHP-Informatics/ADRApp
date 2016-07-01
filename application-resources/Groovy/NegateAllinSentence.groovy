inputAS = doc.getAnnotations(scriptParams.inputAS).get("NegateAll");

for(annot in inputAS){
    inputAS2 = gate.Utils.getContainedAnnotations(inputAS, annot, scriptParams.targetType)
    for(annot2 in inputAS2){
        annot2.getFeatures().put("negate","yes")
    }

}