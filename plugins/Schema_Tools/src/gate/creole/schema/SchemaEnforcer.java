/*
 * SchemaEnforcer.java
 *
 * Copyright (c) 2010, The University of Sheffield.
 *
 * This file is part of GATE (see http://gate.ac.uk/), and is free software,
 * licenced under the GNU Library General Public License, Version 2, June 1991
 * (in the distribution as file licence.html, and also available at
 * http://gate.ac.uk/gate/licence.html).
 *
 * Mark A. Greenwood, 13/08/2010
 */

package gate.creole.schema;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Factory;
import gate.FeatureMap;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.AnnotationSchema;
import gate.creole.ExecutionException;
import gate.creole.FeatureSchema;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import gate.util.InvalidOffsetException;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("serial")
@CreoleResource(name = "Schema Enforcer", interfaceName = "gate.ProcessingResource", icon = "enforce.png", comment = "Produces an annotation set whose content is restricted by the specified set of schemas", helpURL="http://gate.ac.uk/userguide/sec:misc-creole:schemaenforcer")
public class SchemaEnforcer extends AbstractLanguageAnalyser {

  private String inputASName = null;

  private String outputASName = null;

  private boolean useDefaults = false;

  private List<AnnotationSchema> schemas = new ArrayList<AnnotationSchema>();

  @Override
  public void execute() throws ExecutionException {

    // If there are no schemas selected then throw an exception
    if(schemas.isEmpty())
      throw new ExecutionException(
              "At least one schema must be provided for encforcement!");

    // get the annotation set we are going to store the clean annotations into
    AnnotationSet outputAS = getDocument().getAnnotations(outputASName);

    // to ensure a clean set of annotations the output set must be empty before
    // we start adding annotations to it
    if(!outputAS.isEmpty())
      throw new ExecutionException("Output AnnotationSet must be empty");

    // get the set we are going to put the clean annotations into
    AnnotationSet inputAS = getDocument().getAnnotations(inputASName);

    // loop through the schemas we are cleaning against
    for(AnnotationSchema schema : schemas) {

      // get all the annotations of the same type as the current schema
      AnnotationSet annots = inputAS.get(schema.getAnnotationName());

      // if there are no annotations then move onto the next schema
      if(annots == null) continue;

      // for each of the annotations whose type matches the current schema
      for(Annotation a : annots) {

        // let's assume the annotation is valid wrt the schema
        boolean valid = true;

        // create a FeatureMap to hold any features we want to keep
        FeatureMap params = Factory.newFeatureMap();

        // if the schema specifies features then
        if(schema.getFeatureSchemaSet() != null) {

          // get the features from the existing annotation
          FeatureMap current = a.getFeatures();

          // for each of the features specified in the schema...
          for(FeatureSchema fs : schema.getFeatureSchemaSet()) {

            // get the name of the feature and...
            String fn = fs.getFeatureName();

            // the value of that feature from the existing annotation
            Object fv = current.get(fn);

            if(fv != null) {
              if(fs.getFeatureValueClass().isAssignableFrom(fv.getClass())) {
                if(!fs.isEnumeration() || fs.getPermittedValues().contains(fv)) {
                  // if the feature exists and is valid then copy it into the
                  // FeatureMap we will use to create the clean annotation
                  params.put(fn, fv);
                }
              }
            }

            if(fs.isRequired() && !params.containsKey(fn)) {
              String defaultValue = fs.getRawFeatureValue();
              if(useDefaults && defaultValue != null
                      && !defaultValue.equals("")) {
                // if the user wants to use default values and there is one
                // specified
                // then use this instead of marking the annotation invalid
                params.put(fn, defaultValue);
              } else {
                // if the feature specified in the schema is marked as required
                // but we haven't managed to add it to the cleaned annotation
                // for
                // some reason, then the annotation isn't valid so flag this and
                // abort
                valid = false;
                break;
              }
            }
          }
        }

        if(valid) {
          // if we have a valid clean annotation then...
          try {
            // ... add it to the output annotation set
            outputAS.add(a.getId(), a.getStartNode().getOffset(), a
                    .getEndNode().getOffset(), schema.getAnnotationName(),
                    params);
          } catch(InvalidOffsetException e) {
            // this should be completely impossible
            throw new ExecutionException(e);
          }
        }
      }
    }
  }

  @RunTime
  @CreoleParameter(comment = "should we use the default value to add missing required features", defaultValue = "false")
  public void setUseDefaults(Boolean useDefaults) {
    this.useDefaults = useDefaults;
  }

  public Boolean getUseDefaults() {
    return useDefaults;
  }

  @RunTime
  @Optional
  @CreoleParameter(comment = "the annotation set used as input to this PR")
  public void setInputASName(String name) {
    inputASName = name;
  }

  public String getInputASName() {
    return inputASName;
  }

  @RunTime
  @CreoleParameter(comment = "the annotation set used to store output from this PR")
  public void setOutputASName(String name) {
    outputASName = name;
  }

  public String getOutputASName() {
    return outputASName;
  }

  @RunTime
  @CreoleParameter(comment = "the list of schemas that define the annotations to move from the input to the output annotation set")
  public void setSchemas(List<AnnotationSchema> schemas) {
    this.schemas = schemas;
  }

  public List<AnnotationSchema> getSchemas() {
    return schemas;
  }
}
