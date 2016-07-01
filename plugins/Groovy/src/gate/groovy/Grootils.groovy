/*
 *  Grootils.groovy
 *
 *  Copyright (c), The University of Sheffield.
 *
 *  This file is part of the GATE/Groovy integration layer, and is free
 *  software, released under the terms of the GNU Lesser General Public
 *  Licence, version 2.1 (or any later version).  A copy of this licence
 *  is provided in the file LICENCE in the distribution.
 *
 *  Groovy is developed by The Codehaus, details are available from
 *  http://groovy.codehaus.org
 */

package gate.groovy

/** Grootils example code. */
class GrootilsExamples {
  static {
    Grootils.wrap( this, "thingStore", [ "a", "b", "c" ] )
  }

  static void main(String[] args) {
    GrootilsExamples t = new GrootilsExamples()
    println "t.thingStore(): ${t.thingStore()}"
    assert t.a == null
    assert !(t.a)
    t.a = 123
    assert t.a
    assert t.a == 123
    assert t.thingStoreElements == [ "a", "b", "c" ]
    assert t.thingStore() == [a:123, b:null, c:null]
    t.c = "asdaskdjasld"
    assert t.thingStore() == [a:123, b:null, c:"asdaskdjasld"]
    println "t.thingStore(): ${t.thingStore()}"
  }
}

/** Groovy utilities. */
class Grootils {
  /**
   * Adds a map to a class which amalgamates a set of properties so that you
   * can list this set in only one place while still accessing each individual
   * as a simple member variable. Makes it
   * possible to treat each property as a normal class member, then to access
   * a map of the members and their values at any point. Useful for classes
   * which set up a bunch of values (which can be accessed trivially as
   * members) and then provide a map of the values, keyed by their names.
   * <b>NOTE:</b> should be called in a static initialiser of the client
   * class, e.g. given this initialiser in <tt>class Thing</tt>:
   * <pre>
   * static {
   *   Grootils.wrap( this, "thingStore", [ "a", "b", "c" ] )
   * }
   * </pre>
   * we can then write:
   * <pre>
   * Thing t = new Thing()
   * assert t.a == null
   * assert !(t.a)
   * t.a = 123
   * assert t.a
   * assert t.a == 123
   * assert t.thingStoreElements == [ "a", "b", "c" ]
   * assert t.thingStore() == [a:123, b:null, c:null]
   * t.c = "asdaskdjasld"
   * assert t.thingStore() == [a:123, b:null, c:"asdaskdjasld"]
   * </pre>
   * @param clz the class.
   * @param mapName the name of the map.
   * @param keyNames the set of properties.
   */
  static void wrap(Class clz, String mapName, List keyNames) {
    // TODO check not overwriting existing props or methods

    // store the list of properties as "${mapName}Elements"
    clz.metaClass."${mapName}Elements" = keyNames

    // create a new method called "${mapName}" for accessing the map
    clz.metaClass."${mapName}" = { 
      Map m = [:]
      for(String k in keyNames) {
        m[k] = delegate."${k}"
      }
      return m
    }

    // create new properties for each of "${keyNames}"
    for(String k in keyNames) {
      clz.metaClass."${k}" = null
    }
  } // wrap()
} // Grootils
