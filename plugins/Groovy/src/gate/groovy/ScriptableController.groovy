package gate.groovy

import gate.*
import gate.Factory.DuplicationContext;
import gate.creole.*
import gate.creole.metadata.*
import gate.util.*

import groovy.lang.Binding;
import groovy.time.TimeCategory

import java.beans.PropertyChangeSupport
import java.beans.PropertyChangeListener

import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

import org.apache.log4j.Logger

@CreoleResource(name = "Scriptable Controller", comment =
    "A controller whose execution strategy is controlled by a Groovy script",
    helpURL = "http://gate.ac.uk/userguide/sec:api:groovy:controller",
    icon = "/gate/groovy/scriptable-controller")
public class ScriptableController extends SerialController
                          implements CorpusController, LanguageAnalyser {
                            
  protected static final Logger log = Logger.getLogger(ScriptableController)
                            
  /**
   * The corpus over which we are running.
   */
  Corpus corpus

  /**
   * The document over which we are running, when in LanguageAnalyser mode.
   */
  Document document
  
  /**
   * The classloader that acts as the parent of the groovy shell.
   */
  private GateClassLoader classloader
  
  /**
   * The PR (if any) currently being executed.
   */
  private transient volatile ProcessingResource currentPR
  
  /**
   * The executor used to run timeLimit tasks in a background thread.
   */
  private transient ExecutorService timeLimitExecutor

  /**
   * Text of the Groovy script that controls execution.
   */
  String controlScript = """\
eachDocument {
  allPRs()
}"""

  /**
   * Explicit setter to fire PropertyChangeEvent.
   */
  public void setControlScript(String newScript) {
    def oldScript = controlScript
    controlScript = newScript
    // clear the cached compiled script if the script text has been changed
    if(controlScript != oldScript) {
      script = null
    }
    pcs.firePropertyChange("controlScript", oldScript, newScript)
  }

  private Script script

  /**
   * Map from PR name to List of the indexes into the prList at which PRs with
   * that name are found.  Only valid during a call to executeImpl.
   */
  private transient Map prsByName

  /**
   * Check for interruption, throwing an exception if required.
   */
  protected void checkInterrupted() throws ExecutionException {
    if(isInterrupted()) {
      throw new ExecutionInterruptedException("The execution of the " +
          name + " application has been abruptly interrupted")
    }
  }

  /**
   * Parse the control script and set up its metaclass.
   */
  protected void parseScript() throws ExecutionException {
    try {
      if (classloader != null) Gate.getClassLoader().forgetClassLoader(classloader)
      
      classloader = Gate.getClassLoader().getDisposableClassLoader(getName()+System.currentTimeMillis(), ScriptPR.class.getClassLoader(), true)
      
      script = new GroovyShell(classloader).parse(
          controlScript + "\n\n\n" + GroovySupport.STANDARD_IMPORTS)
      // replace the binding with our "active" one that delegates
      // corpus, controller and prs variables through to the controller
      script.binding = new ScriptableControllerBinding(this, script.binding)
      GroovySystem.metaClassRegistry.removeMetaClass(script.getClass())
      def mc = script.getClass().metaClass

      // this closure runs a single PR from the PR list, with an optional set
      // of parameter value overrides.  The original values for these
      // parameters are restored after the PR has been run.
      def runPr =  { params, index ->
        def pr = prList[index]
        checkInterrupted()
        FeatureMap savedParams = [:].toFeatureMap()
        try {
          // save original parameter values
          params.each { k, v ->
            savedParams[k] = pr.getParameterValue(k)
            pr.setParameterValue(k, v)
          }
          // inject the corpus and current document (if any)
          if(pr instanceof LanguageAnalyser) {
            if(corpus) {
              pr.corpus = corpus
            }
            // check if the script knows about a current document
            if(script.binding.variables.doc) {
              pr.document = script.binding.variables.doc
            }
          }
          // execute the PR using SerialController.runComponent
          currentPR = pr
          runComponent(index)
        }
        finally {
          currentPR = null
          if(pr instanceof LanguageAnalyser) {
            pr.corpus = null
            pr.document = null
          }
          pr.setParameterValues(savedParams)
        }
      }

      mc.invokeMethod = { String name, args ->
        checkInterrupted()
        // somePR() or somePR(param1:val1, param2:val2)
        if(prsByName.containsKey(name) && (!args || args[0] instanceof Map)) {
          def params = args ? args[0] : [:]
          prsByName[name].each(runPr.curry(params))
        }
        // eachDocument { ... }
        else if("eachDocument".equals(name) && args && args[0] instanceof Closure) {
          eachDocument(args[0])
        }
        // allPRs()
        else if("allPRs".equals(name) && !args) {
          // special case - allPrs() runs all the PRs in order
          (0 ..< prList.size()).each(runPr.curry([:]))
        }
        // ignoringErrors { ... }
        else if("ignoringErrors".equals(name) && args?.length == 1 && (args[0] instanceof Closure)) {
          try {
            args[0].call()
          }
          catch(ThreadDeath td) {
            // special case - rethrow ThreadDeath. This is in case someone puts
            // an ignoringErrors block *inside* a timeLimit block, we don't want
            // to gobble up the hard limit stop() signal.
            throw td
          }
          catch(Throwable err) {
            log.warn("Ignored error", err)
          }
        }
        // timeLimit(60000) {} or timeLimit(1.minute) {} (treated as a soft limit)
        // or timeLimit(soft:1.minute, exception:30.seconds, hard:45.seconds) {}
        else if("timeLimit".equals(name) && args?.length == 2 && (args[1] instanceof Closure)
            && ((args[0] instanceof Map && args[0].soft || args[0].hard || args[0].exception)
               || args[0] instanceof Number || args[0].respondsTo("toMilliseconds"))) {
          def timeouts = args[0]
          if(!(timeouts instanceof Map)) {
            timeouts = [soft:timeouts]
          }
          timeLimit(timeouts, args[1])
        }
        else {
          // support for methods declared in the script itself
          MetaMethod mm = mc.getMetaMethod(name, args)
          if(mm) {
            return mm.invoke(delegate, args)
          }
          else {
            throw new MissingMethodException(name, getClass(), args)
          }
        }
      }

      script.metaClass = mc
    }
    catch(Exception e) {
      throw new ExecutionException("Error parsing control script", e)
    }
  }
  
  /**
   * Iterate over this controller's corpus, calling the supplied
   * closure once for each document in the corpus.
   */
  protected void eachDocument(Closure c) throws ExecutionException {
    if(corpus == null) {
      throw new ExecutionException(
        "eachDocument not permitted when controller has no corpus")
    }
    def savedCurrentDoc = script.binding.variables.doc
    try {
      if(document) {
        // we are a language analyser, so just process the single document
        script.binding.setVariable('doc', document)
        c.call(document)
      }
      else {
        benchmarkFeatures.put(Benchmark.CORPUS_NAME_FEATURE, corpus.name)

        // process each document in the corpus - corpus.each does the
        // right thing with corpora stored in datastores
        corpus.each {
          String savedBenchmarkId = getBenchmarkId()
          try {
            // include the document name in the benchmark ID for sub-events
            setBenchmarkId(Benchmark.createBenchmarkId("doc_${it.name}",
                    getBenchmarkId()))
            benchmarkFeatures.put(Benchmark.DOCUMENT_NAME_FEATURE, it.name)
            checkInterrupted()
            script.binding.setVariable('doc', it)
            c.call(it)
          }
          finally {
            setBenchmarkId(savedBenchmarkId)
            benchmarkFeatures.remove(Benchmark.DOCUMENT_NAME_FEATURE)
          }
        }
      }
    }
    finally {
      script.binding.setVariable('doc', savedCurrentDoc)
      benchmarkFeatures.remove(Benchmark.CORPUS_NAME_FEATURE)
    }
  }
  
  protected void timeLimit(Map timeouts, Closure c) throws ExecutionException {
    // convert any Durations back to milliseconds.  This allows us to use the groovy
    // TimeCategory to say things like timeLimit(soft:1.minute+30.seconds, hard:5.minutes) {...}
    timeouts.each { Map.Entry e ->
      if(e.value.respondsTo("toMilliseconds")) {
        e.value = e.value.toMilliseconds()
      } else {
        e.value = e.value as Long
      }
    }
    
    Thread runningThread = null
    // a closure that catches exceptions and returns them
    def callable = {
      runningThread = Thread.currentThread()
      try {
        // need to use(TimeCategory) again here, as use
        // scopes are thread-local
        use(TimeCategory, c)
      }
      catch(InterruptedException e) {
        // something was interrupted
        return new ExecutionInterruptedException("The execution of the " +
          name + " application has been abruptly interrupted", e)
      }
      catch(Throwable t) {
        return t
      }
      finally {
        runningThread = null
      }
      return null
    }
    
    Future<Throwable> future = executor.submit(callable as Callable<Throwable>)
    if(timeouts.soft) {
      try {
        Throwable exception = future.get(timeouts.soft, TimeUnit.MILLISECONDS)
        if(exception) {
          throw exception
        }
        // finished successfully
        return
      }
      catch(TimeoutException te) {
        // we have hit the soft timeout - try and stop the thread
        // gently
        log.info("ScriptableController.timeLimit: soft limit reached")
        Thread t = runningThread
        if(t) t.interrupt()
        currentPR?.interrupt()
      }
      catch(InterruptedException ie) {
        Thread.currentThread().interrupt()
      }
    }
    if(timeouts.exception) {
      try {
        Throwable exception = future.get(timeouts.exception, TimeUnit.MILLISECONDS)
        if(exception) {
          throw exception
        }
        // finished successfully
        return
      }
      catch(TimeoutException te) {
        // we have hit the exception timeout - try and induce an exception
        log.info("ScriptableController.timeLimit: 'exception' limit reached, " +
                 "attempting to induce an exception")
        def pr = currentPR
        if(pr instanceof LanguageAnalyser) {
          pr.corpus = null
          pr.document = null
        }
      }
      catch(InterruptedException ie) {
        Thread.currentThread().interrupt()
      }
    }
    if(timeouts.hard) {
      try {
        Throwable exception = future.get(timeouts.hard, TimeUnit.MILLISECONDS)
        if(exception) {
          throw exception
        }
        // finished successfully
        return
      }
      catch(TimeoutException te) {
        // we have hit the hard timeout
        log.info("ScriptableController.timeLimit: hard limit reached, " +
                 "terminating thread")
        Thread t = runningThread
        if(t) t.stop()
        throw new ExecutionInterruptedException("ScriptableController.timeLimit: hard limit reached")
      }
      catch(InterruptedException ie) {
        Thread.currentThread().interrupt()
      }
    }
    
    // if we get here we don't have a hard limit, so wait until task is complete
    Throwable exception = future.get()
    if(exception) {
      throw exception
    }
  }
  
  /**
   * Create the timeLimit executor lazily the first time it is requested.
   * @return
   */
  private ExecutorService getExecutor() {
    if(!timeLimitExecutor) {
      // use a cached thread pool rather than a single thread
      // executor to avoid deadlock in the case of nested
      // timeLimit calls.
      timeLimitExecutor = Executors.newCachedThreadPool(new DaemonThreadFactory())
    }
    return timeLimitExecutor
  }

  protected void executeImpl() throws ExecutionException {
    interrupted = false
    if(script == null) {
      parseScript()
    }
    // build the prsByName map
    prsByName = (0 ..< prList.size()).groupBy { prList[it].name }

    if(log.isDebugEnabled()) {
      prof.initRun("Execute controller [" + getName() + "]");
    }

    // Set initial variable values
    if(document) {
      script.binding.setVariable('doc', document)
    }
    try {
      // run the script using the Groovy TimeCategory to allow nicer
      // specification of the timeouts in a timeLimit block
      use(TimeCategory, script.&run)
    }
    catch(ExecutionException e) {
      throw e
    }
    catch(RuntimeException e) {
      throw e
    }
    catch(Exception e) {
      throw new ExecutionException(e)
    }
    finally {
      prsByName = null
    }

    if(log.isDebugEnabled()) {
      prof.checkPoint("Execute controller [" + getName() + "] finished");
    }
  }
  
  public void cleanup() {
    timeLimitExecutor?.shutdownNow()
    super.cleanup()
    if (classloader != null) Gate.getClassLoader().forgetClassLoader(classloader)
  }

  /**
   * Always return an empty list for "offending" PRs - even if a parameter is
   * not set before execution, it might be set dynamically by the script at
   * runtime.
   *
   * Yes, this is another typo in the superclass, the method really is called
   * getOffendingP(r)ocessingResources.
   */
  public List<ProcessingResource> getOffendingPocessingResources() {
    return []
  }

  /**
   * Copy the control script text when duplicating a ScriptableController.
   */
  public Resource duplicate(DuplicationContext ctx)
          throws ResourceInstantiationException {
    ScriptableController dup = (ScriptableController)super.duplicate(ctx);
    dup.controlScript = this.controlScript
    return dup
  }
  /**
   * Property change support for the controlScript bound property.
   */
  private PropertyChangeSupport pcs = new PropertyChangeSupport(this)

  public void addPropertyChangeListener(String propName, PropertyChangeListener l) {
    pcs.addPropertyChangeListener(propName, l)
  }

  public void removePropertyChangeListener(String propName, PropertyChangeListener l) {
    pcs.removePropertyChangeListener(propName, l)
  }
}
                          
/**
 * ThreadFactory implementation that fetches threads from the
 * default ThreadFactory but sets their daemon status before
 * returning them.
 */
class DaemonThreadFactory implements ThreadFactory {
  private ThreadFactory dtf = Executors.defaultThreadFactory()
  
  public Thread newThread(Runnable r) {
    Thread t = dtf.newThread(r)
    t.daemon = true
    return t
  }
}

/**
 * Specialized Binding for scriptable controller scripts that
 * intercepts get and set calls to certain variables and redirects
 * them to properties of the controller.  In particular:
 * <dl>
 *   <dt>controller</dt>
 *   <dd>(read-only) a reference to the controller that owns this
 *       script.</dd>
 *   <dt>corpus</dt>
 *   <dd>(read-write) the corpus of the current controller.  Thus
 *       an assignment "corpus = ..." in the script is actually
 *       a call to controller.setCorpus.</dd>
 *   <dt>prs</dt>
 *   <dd>(read-only) an unmodifiable wrapper around the controller's
 *       prList.</dd>
 * </dl>
 */
class ScriptableControllerBinding extends Binding {
  private ScriptableController controller
  
  private List unmodifiablePrList
  
  ScriptableControllerBinding(ScriptableController controller,
            Binding delegateBinding) {
    super(delegateBinding.getVariables());
    this.controller = controller
    unmodifiablePrList = controller.prList.asImmutable()
  }
  
  public getVariable(String name) {
    switch(name) {
      case 'controller': return controller; break
      case 'corpus': return controller.corpus; break
      case 'prs': return unmodifiablePrList; break
      default: return super.getVariable(name); break
    }
  }
  
  public void setVariable(String name, value) {
    switch(name) {
      case 'controller':
      case 'prs':
        throw new ReadOnlyPropertyException(name, this.getClass()); break
        
      case 'corpus': controller.corpus = value; break
      
      default: super.setVariable(name, value); break
    }
  }
}
