/*
 *  PubmedUtils.java
 *
 *  Copyright (c) 1995-2012, The University of Sheffield. See the file
 *  COPYRIGHT.txt in the software or at http://gate.ac.uk/gate/COPYRIGHT.txt
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Library General Public License,
 *  Version 2, June 1991 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
 *
 *  Valentin Tablan, 18 Jan 2013
 *
 *  $Id: PubmedUtils.java 17670 2014-03-15 09:18:40Z markagreenwood $
 */
package gate.corpora;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * A class with static utility methods for Pubmed-style format parsers. 
 */
public class PubmedUtils {

  /**
   * Adds a new field value to the set of known values. If there is no value 
   * already associated with the given field name, then the new field value is
   * added to the map as a String value. If there is already a single String
   * value associated with the given field name, then the field value is 
   * converted from a String to a LinkedList<String>, and the new value is 
   * appended to the list. If there are already multiple values associated with the
   * field, then the new value is simply appended to the existing list.
   *  
   * @param fieldName the name for the field
   * @param fieldValue the new value for the field
   * @param fieldValues a map from field name to either String or 
   *  LinkedList<String>
   */
  public static void addFieldValue(String fieldName, String fieldValue, 
      Map<String, Serializable> fieldValues) {
    Serializable oldValue = fieldValues.get(fieldName);
    if(oldValue == null) {
      fieldValues.put(fieldName, fieldValue);
    } else if(oldValue instanceof String) {
      LinkedList<String> newValue = new LinkedList<String>();
      newValue.add((String)oldValue);
      newValue.add(fieldValue);
      fieldValues.put(fieldName, newValue);
    } else { // old value must be a LinkedList<String>
      @SuppressWarnings("unchecked")
      LinkedList<String> newValue = (LinkedList<String>)oldValue;
      newValue.add(fieldValue);
    } 
  }

  /**
   * Given a metadata field (which may be a String or a List<String> value), 
   * this produces the corresponding String representation, while trapping all
   * exceptions.
   * @param fieldValue
   * @return a String representation for the supplied field value. 
   */
  public static String getFieldValueString(Object fieldValue) {
    if(fieldValue == null) return "";
    if(fieldValue instanceof String) {
      return (String) fieldValue;
    } else if(fieldValue instanceof List<?>) {
      StringBuilder str = new StringBuilder();
      boolean first = true;
      for(Object val : (List<?>) fieldValue) {
        if(!first){
          str.append(", ");
        }
        if(val != null){
          try{
            str.append(val.toString());
            if(first){
              first = false;
            }
          } catch (Exception e) {
            // ignore
          }
        }
      }
      return str.toString();
    } else {
      try{
        return fieldValue.toString();
      } catch (Exception e) {
        return "";
      }
    }
  }
}
