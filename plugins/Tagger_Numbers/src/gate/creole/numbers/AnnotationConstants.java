/*
 * Copyright (c) 2011, The University of Sheffield.
 * 
 * This file is part of GATE (see http://gate.ac.uk/), and is free software,
 * licenced under the GNU Library General Public License, Version 3, June 2007
 * (in the distribution as file licence.html, and also available at
 * http://gate.ac.uk/gate/licence.html).
 */
package gate.creole.numbers;

/**
 * To ensure that all annotations/features created by the different number PRs
 * use the same names this class holds constants specifying the correct values.
 * 
 * @author Mark A. Greenwood
 */
public final class AnnotationConstants {
  
  /**
   * The constant to be used for the name of a number annotation.
   */
  public static final String NUMBER_ANNOTATION_NAME = "Number";

  /**
   * The constant to be used for the name of the type feature.
   */
  public static final String TYPE_FEATURE_NAME = "type";

  /**
   * The constant to be used for the name of the value feature.
   */
  public static final String VALUE_FEATURE_NAME = "value";
  
  /**
   * The constant to be used for the name of the hint feature.
   */
  public static final String HINT_FEATURE_NAME = "hint";
}
