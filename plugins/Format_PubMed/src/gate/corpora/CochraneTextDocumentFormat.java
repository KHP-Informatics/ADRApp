/*
 *  CochraneTextDocumentFormat.java
 *
 *  Copyright (c) 1995-2012, The University of Sheffield. See the file
 *  COPYRIGHT.txt in the software or at http://gate.ac.uk/gate/COPYRIGHT.txt
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Library General Public License,
 *  Version 2, June 1991 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
 *
 *  Valentin Tablan, 17 May 2012
 *
 *  $Id: CochraneTextDocumentFormat.java 17670 2014-03-15 09:18:40Z markagreenwood $
 */
package gate.corpora;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import gate.AnnotationSet;
import gate.Document;
import gate.Factory;
import gate.GateConstants;
import gate.Resource;
import gate.creole.ResourceInstantiationException;
import gate.creole.metadata.AutoInstance;
import gate.creole.metadata.CreoleResource;
import gate.util.DocumentFormatException;
import gate.util.InvalidOffsetException;
import gate.util.Strings;

/**
 * A document format analyser for Cochrane text documents. Use mime type value 
 * "text/x-cochrane", or file extension ".cochrane.txt" to access this document
 * format. 
 */
@CreoleResource(name="GATE .cochrane.txt document format",
  comment="<html>Load this to allow the opening of Cochrane text documents, " +
      "and choose the mime type <strong>\"text/x-cochrane\"</strong>, or use " +
      "the correct file extension.", 
  autoinstances = {@AutoInstance(hidden=true)},
  isPrivate = true)
public class CochraneTextDocumentFormat extends TextualDocumentFormat {

  private static final long serialVersionUID = 8362288605943414676L;

  private static final String COCHRANE_TITLE = "TI";
  
  private static final String COCHRANE_ABSTRACT = "AB";
  
  private static final String COCHRANE_AUTHORS = "AU";
  
  private static final String COCHRANE_ID = "ID";
  
  protected static final Logger logger = Logger.getLogger(
      CochraneTextDocumentFormat.class);
  
  /* (non-Javadoc)
   * @see gate.DocumentFormat#supportsRepositioning()
   */
  @Override
  public Boolean supportsRepositioning() {
    return false;
  }

  
  /* (non-Javadoc)
   * @see gate.corpora.TextualDocumentFormat#init()
   */
  @Override
  public Resource init() throws ResourceInstantiationException {
    MimeType mime = new MimeType("text","x-cochrane");
    // Register the class handler for this mime type
    mimeString2ClassHandlerMap.put(mime.getType()+ "/" + mime.getSubtype(),
                                                                          this);
    // Register the mime type with mine string
    mimeString2mimeTypeMap.put(mime.getType() + "/" + mime.getSubtype(), mime);
    // Register file sufixes for this mime type
    suffixes2mimeTypeMap.put("cochrane.txt", mime);
    // Set the mimeType for this language resource
    setMimeType(mime);
    return this;
  }
  
  /* (non-Javadoc)
   * @see gate.corpora.TextualDocumentFormat#cleanup()
   */
  @Override
  public void cleanup() {
    super.cleanup();
    
    MimeType mime = getMimeType();
    
    mimeString2ClassHandlerMap.remove(mime.getType()+ "/" + mime.getSubtype());
    mimeString2mimeTypeMap.remove(mime.getType() + "/" + mime.getSubtype());
    suffixes2mimeTypeMap.remove("cochrane.txt");
  }
  
  /* (non-Javadoc)
   * @see gate.corpora.TextualDocumentFormat#unpackMarkup(gate.Document)
   */
  @Override
  public void unpackMarkup(Document doc) throws DocumentFormatException {
    try {
      BufferedReader content = new BufferedReader(new StringReader(
          doc.getContent().toString()));
      Map<String, Serializable> fields = new HashMap<String, Serializable>();
      String line = content.readLine();
      String key = null;
      StringBuilder value = new StringBuilder();
      Pattern linePatt =  Pattern.compile("([A-Z]+): (.*)");
      while(line!= null) {
        Matcher matcher = linePatt.matcher(line);
        if(matcher.matches()) {
          // new field
          if(key != null) {
            // save old value
            PubmedUtils.addFieldValue(key, value.toString(), fields);
          }
          key = matcher.group(1).trim();
          value.delete(0, value.length());
          value.append(matcher.group(2));
        } else {
          // a non-assignment line -> append to previous value
          if(value.length() == 0) {
            logger.warn("Ignoring invalid input line:\""  +
              line +  "\"");
          } else {
            value.append(Strings.getNl()).append(line.trim());
          }
        }
        line = content.readLine();
      }
      if(key != null) {
        // save old value
        PubmedUtils.addFieldValue(key, value.toString(), fields);
      }
      StringBuilder docText = new StringBuilder();
      // add document title
      int titleStart = docText.length();
      int titleEnd = titleStart;
      Serializable aField = fields.remove(COCHRANE_TITLE);
      if(aField != null) {
        docText.append(PubmedUtils.getFieldValueString(aField));
        titleEnd = docText.length();
        docText.append(Strings.getNl()).append(Strings.getNl());
      } else {
        String docName = doc.getName();  
        logger.warn("Could not find document title in document " + 
            (docName != null ? docName : ""));
      }
      // add ID
      int idStart = docText.length();
      int idEnd = idStart;
      aField = fields.get(COCHRANE_ID);
      if(aField != null) {
        docText.append(PubmedUtils.getFieldValueString(aField));
        idEnd = docText.length();
        docText.append(Strings.getNl()).append(Strings.getNl());
      } else {
        String docName = doc.getName();  
        logger.warn("Could not find document ID in document " + 
            (docName != null ? docName : ""));
      }      
      // add authors
      int authorStart = docText.length();
      int authorEnd = authorStart;
      aField = fields.get(COCHRANE_AUTHORS);
      if(aField != null) {
        docText.append(PubmedUtils.getFieldValueString(aField));
        authorEnd = docText.length();
        docText.append(Strings.getNl()).append(Strings.getNl());
      } else {
        String docName = doc.getName();  
        logger.warn("Could not find document authors in document " + 
            (docName != null ? docName : ""));
      }
      // and the document abstract
      aField = fields.remove(COCHRANE_ABSTRACT);
      int absStart = docText.length();
      if(aField != null) {
        docText.append(PubmedUtils.getFieldValueString(aField));
      } else {
        String docName = doc.getName();  
        logger.warn("Could not find document abstract in document " + 
            (docName != null ? docName : ""));
      }
      int absEnd = docText.length();
      doc.setContent(new DocumentContentImpl(docText.toString()));
      
      AnnotationSet origMkups = doc.getAnnotations(
          GateConstants.ORIGINAL_MARKUPS_ANNOT_SET_NAME);
      if(titleEnd > titleStart){
        origMkups.add((long)titleStart, (long)titleEnd, "title", 
            Factory.newFeatureMap());
      }
      if(idEnd > idStart){
        origMkups.add((long)idStart, (long)idEnd, "id", 
            Factory.newFeatureMap());
      }      
      if(authorEnd > authorStart) {
        origMkups.add((long)authorStart, (long)authorEnd, "authors", 
            Factory.newFeatureMap());
      }      
      if(absEnd > absStart) {
        origMkups.add((long)absStart, (long)absEnd, "abstract", 
            Factory.newFeatureMap());
      }
      // everything else becomes document features
      doc.getFeatures().putAll(fields);
    } catch(IOException e) {
      throw new DocumentFormatException("Error while unpacking markup",e); 
    } catch(InvalidOffsetException e) {
      throw new DocumentFormatException("Error while unpacking markup",e);
    }
    
    // now let the text unpacker also do its job
    super.unpackMarkup(doc);
  }  
  
}
