/*
 * Units is a program for unit conversion originally written in C by Adrian
 * Mariano (adrian@cam.cornell.edu.). Copyright (C) 1996, 1997, 1999, 2000,
 * 2001, 2002, 2003, 2004, 2005, 2006 by Free Software Foundation, Inc.
 * 
 * Java version Copyright (C) 2003, 2004, 2005, 2006, 2007 by Roman R
 * Redziejowski (roman.redz@tele2.se).
 * 
 * GATE version is Copyright (c) 2009-2011, The University of Sheffield.
 * 
 * This file is part of GATE (see http://gate.ac.uk/), and is free software,
 * Licensed under the GNU Library General Public License, Version 3, June 2007
 * (in the distribution as file licence.html, and also available at
 * http://gate.ac.uk/gate/licence.html).
 */
package gate.creole.measurements;

import java.util.List;

/**
 * A unit, prefix, or function.
 */
abstract class Entity {
  /** Name of this Entity. */
  String name;

  MeasurementsParser gnuUnits;

  /**
   * Constructor.
   * 
   * @param nam
   *          name.
   * @param loc
   *          where defined.
   */
  Entity(final String nam, MeasurementsParser gnuUnits) {
    name = nam;
    this.gnuUnits = gnuUnits;
  }

  /**
   * Check the definition.Used in 'checkunits'.
   */
  abstract void check();

  /**
   * If object defined by this Entity is compatible with Value 'v', add this
   * Entity to 'list'. Used in 'tryallunits'.
   */
  abstract void addtolist(final Measurement v, List<Entity> list);

  /**
   * Return short description of the defined object to be shown by
   * 'tryallunits'.
   */
  abstract String desc();

  /**
   * Inserts this Entity into a given Vector in the increasing alphabetic order
   * of <code>name</code> fields. <br>
   * (This method replaces more advanced features present in the later releases
   * of Java. Remember we are supposed to work under release 1.1.)
   * 
   * @param v
   *          Vector to insert in.
   */
  @SuppressWarnings("unchecked")
  void insertAlph(@SuppressWarnings("rawtypes") List v) {
    // TODO replace with normal Java code to simplify things
    // ---------------------------------------------------------------
    // We use binary search for insertion place.
    // Place for insertion is at index i, left<=i<=right.
    // Initially, it is somewhere in the whole Vector.
    // ---------------------------------------------------------------
    int left = 0;
    int right = v.size();
    // ---------------------------------------------------------------
    // Repeat until the insertion place is unique.
    // ---------------------------------------------------------------
    while(left != right) {
      // Take an element at index middle, left<=middle<right.
      int middle = (left + right) / 2;
      int c = name.compareTo(((Entity)v.get(middle)).name);
      // If s lower than middle element..
      // ..it must be inserted to the left of it.
      if(c < 0)
        right = middle;
      // Otherwise it will be inserted..
      // ..to the right of the middle.
      else left = middle + 1;
    }
    // ---------------------------------------------------------------
    // left==right, insert.
    // ---------------------------------------------------------------
    v.add(left, this);
  }
}
