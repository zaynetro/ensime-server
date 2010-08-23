package org.ensime.server
import java.io.File
import org.ensime.config.ProjectConfig
import org.ensime.model._
import org.ensime.protocol.ProtocolConversions
import org.ensime.util._
import org.ensime.util.RichFile._
import scala.actors._
import scala.actors.Actor._
import scala.collection.{Iterable}
import scala.tools.nsc.{Settings}
import scala.tools.nsc.ast._
import scala.tools.nsc.util.{OffsetPosition}

case class FullTypeCheckCompleteEvent()

class Analyzer(val project:Project, val protocol:ProtocolConversions, config:ProjectConfig) extends Actor with RefactoringController{
  protected val settings = new Settings(Console.println)
  settings.processArguments(config.compilerArgs, false)
  settings.usejavacp.value = false
  protected val reporter = new PresentationReporter()
  protected val cc:RichCompilerControl = new RichPresentationCompiler(settings, reporter, this, config)
  protected var awaitingInitialCompile = true


  import cc._
  import protocol._

  def act(){
    cc.askNewRunnerThread
    project ! SendBackgroundMessageEvent("Initializing Scala Analyzer...")
    cc.askReloadAllFiles
    loop {
      try{
	receive {
	  case AnalyzerShutdownEvent() =>
	  {
	    cc.askClearTypeCache
	    cc.askShutdown()
	    exit('stop)
	  }

	  case FullTypeCheckCompleteEvent() =>
	  {
	    if(awaitingInitialCompile){
	      project ! AnalyzerReadyEvent()
	      awaitingInitialCompile = false
	    }
	    val result = NoteList('scala, true, reporter.allNotes)
	    project ! TypeCheckResultEvent(result)
	  }

	  case RPCCommandEvent(req:Any) => {
	    try{
	      if(awaitingInitialCompile){
		project ! RPCCommandErrorEvent("Scala Analyzer is not ready! Please wait.")
	      }
	      else{
		req match {
		  case ReloadAllReq() =>
		  {
		    cc.askReloadAllFiles()
		  }
		  case ReloadFileReq(file:File) =>
		  {
		    val f = cc.sourceFileForPath(file.getAbsolutePath())
		    cc.askReloadFile(f)
		    val result = NoteList('scala, false, reporter.allNotes)
		    project ! TypeCheckResultEvent(result)
		  }
		}
	      }
	    }
	    catch{
	      case e:Exception =>
	      {
		System.err.println("Error handling RPC Command: " + e + " :\n" + e.getStackTraceString)
		project ! RPCCommandErrorEvent("Error occurred in Scala Analyzer. Check the server log.")
	      }
	    }
	  }
	  case RPCRequestEvent(req:Any, callId:Int) => {
	    try{

	      if(awaitingInitialCompile){
		project ! RPCErrorEvent(
		  "Scala Analyzer is not ready! Please wait.", callId)
	      }
	      else{

		req match {

		  case req:RefactorPerformReq =>
		  {
		    handleRefactorRequest(req, callId)
		  }

		  case req:RefactorExecReq =>
		  {
		    handleRefactorExec(req, callId)
		  }

		  case req:RefactorCancelReq =>
		  {
		    handleRefactorCancel(req, callId)
		  }

		  case RemoveFileReq(file:File) => 
		  {
		    val f = cc.sourceFileForPath(file.getAbsolutePath())
		    cc.removeUnitOf(f)
		  }

		  case ScopeCompletionReq(file:File, point:Int, prefix:String, constructor:Boolean) => 
		  {
		    val f = cc.sourceFileForPath(file.getAbsolutePath())
		    val p = new OffsetPosition(f, point)
		    val syms = cc.askCompleteSymbolAt(p, prefix, constructor)
		    project ! RPCResultEvent(toWF(syms.map(toWF)), callId)
		  }

		  case TypeCompletionReq(file:File, point:Int, prefix:String) => 
		  {
		    val f = cc.sourceFileForPath(file.getAbsolutePath())
		    val p = new OffsetPosition(f, point)
		    val members = cc.askCompleteMemberAt(p, prefix)
		    project ! RPCResultEvent(toWF(members.map(toWF)), callId)
		  }

		  case InspectTypeReq(file:File, point:Int) =>
		  {
		    val f = cc.sourceFileForPath(file.getAbsolutePath())
		    val p = new OffsetPosition(f, point)
		    val inspectInfo = cc.askInspectTypeAt(p)
		    project ! RPCResultEvent(toWF(inspectInfo), callId)
		  }

		  case InspectTypeByIdReq(id:Int) =>
		  {
		    val inspectInfo = cc.askInspectTypeById(id)
		    project ! RPCResultEvent(toWF(inspectInfo), callId)
		  }

		  case SymbolAtPointReq(file:File, point:Int) =>
		  {
		    val f = cc.sourceFileForPath(file.getAbsolutePath())
		    val p = new OffsetPosition(f, point)
		    val info = cc.askSymbolInfoAt(p)
		    project ! RPCResultEvent(toWF(info), callId)
		  }

		  case InspectPackageByPathReq(path:String) =>
		  {
		    val packageInfo = cc.askPackageByPath(path)
		    project ! RPCResultEvent(toWF(packageInfo), callId)
		  }

		  case TypeAtPointReq(file:File, point:Int) =>
		  {
		    val f = cc.sourceFileForPath(file.getAbsolutePath())
		    val p = new OffsetPosition(f, point)
		    val typeInfo = cc.askTypeInfoAt(p)
		    project ! RPCResultEvent(toWF(typeInfo), callId)
		  }

		  case TypeByIdReq(id:Int) =>
		  {
		    val tpeInfo = cc.askTypeInfoById(id)
		    project ! RPCResultEvent(toWF(tpeInfo), callId)
		  }

		  case CallCompletionReq(id:Int) =>
		  {
		    val callInfo = cc.askCallCompletionInfoById(id)
		    project ! RPCResultEvent(toWF(callInfo), callId)
		  }
		}
	      }
	    }
	    catch{
	      case e:Exception =>
	      {
		System.err.println("Error handling RPC: " + e + " :\n" + e.getStackTraceString)
		project ! RPCErrorEvent("Error occurred in Scala Analyzer. Check the server log.", callId)
	      }
	    }
	  }
	  case other => 
	  {
	    println("Scala Analyzer: WTF, what's " + other)
	  }
	}

      }
      catch{
	case e:Exception =>
	{
	  System.err.println("Error at Compiler message loop: " + e + " :\n" + e.getStackTraceString)
	}
      }
    }
  }

  override def finalize() {
    System.out.println("Finalizing compilation actor.")
  }

}

