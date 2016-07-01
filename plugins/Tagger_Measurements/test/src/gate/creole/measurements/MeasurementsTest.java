/*
 * Copyright (c) 2011, The University of Sheffield.
 * 
 * This file is part of GATE (see http://gate.ac.uk/), and is free software,
 * Licensed under the GNU Library General Public License, Version 3, June 2007
 * (in the distribution as file licence.html, and also available at
 * http://gate.ac.uk/gate/licence.html).
 */

package gate.creole.measurements;

import gate.Gate;
import gate.util.GateException;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;

import junit.framework.TestCase;

/**
 * @author Mark A. Greenwood
 */
public class MeasurementsTest extends TestCase {

  private static MeasurementsParser parser;
  
  @Override
  public void setUp() throws MalformedURLException, IOException, GateException  {
    if (!Gate.isInitialised()) {
      Gate.init();
    }    
    
    File baseDir = new File(Gate.getPluginsHome(), "Tagger_Measurements");
    
    parser = new MeasurementsParser((new File(baseDir, "resources/units.dat")).toURI().toURL(), new File(baseDir, "resources/common_words.txt").toURI().toURL());    
  }

  @Override
  public void tearDown() {
    parser = null;
  }

  public void test6Feet() {
    Measurement m = parser.parse(6d, "Mark was 6 feet 6 inches tall.", 11);
    assertNotNull(m);
        
    assertEquals("length", m.getDimension());
    
    assertEquals(6d, m.getValue());
    assertEquals("feet", m.getUnit());
    assertEquals("feet", m.getParsedText());
    
    assertEquals(1.8288, m.getNormalizedValue());
    assertEquals("m", m.getNormalizedUnit());
  }
  
  public void test6Inches() {
    Measurement m = parser.parse(6d, "Mark was 6 feet 6 inches tall.", 18);
    assertNotNull(m);
    
    assertEquals("length", m.getDimension());
    
    assertEquals(6d, m.getValue());
    assertEquals("inches", m.getUnit());
    assertEquals("inches", m.getParsedText());
    
    assertEquals(0.1524, m.getNormalizedValue());
    assertEquals("m", m.getNormalizedUnit());
  }
  
  public void testLasix() {
    Measurement m = parser.parse(20d, "Lasix 20 mg p.o.", 9);
    assertNotNull(m);
    
    assertEquals("mass", m.getDimension());
    
    assertEquals(20d, m.getValue());
    assertEquals("mg", m.getUnit());
    assertEquals("mg", m.getParsedText());
        
    assertEquals(2.0E-5, m.getNormalizedValue());
    assertEquals("kg", m.getNormalizedUnit());    
  }
}
