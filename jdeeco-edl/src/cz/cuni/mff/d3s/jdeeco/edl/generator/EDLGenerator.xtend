/*
 * generated by Xtext
 */
package cz.cuni.mff.d3s.jdeeco.edl.generator

import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.xtext.generator.IGenerator
import org.eclipse.xtext.generator.IFileSystemAccess
import cz.cuni.mff.d3s.jdeeco.edl.model.edl.*
import cz.cuni.mff.d3s.jdeeco.edl.utils.*
import java.util.HashMap
import java.util.Map
import com.google.inject.Inject
import cz.cuni.mff.d3s.jdeeco.edl.functions.IFunctionRegistry
import cz.cuni.mff.d3s.jdeeco.edl.PrimitiveTypes
import cz.cuni.mff.d3s.jdeeco.edl.typing.ITypeInformationProvider
import cz.cuni.mff.d3s.jdeeco.edl.typing.DefaultTypeInformationProvider
import cz.cuni.mff.d3s.jdeeco.edl.validation.NullErrorReportingService

/**
 * Generates code from your model files on save.
 * 
 * see http://www.eclipse.org/Xtext/documentation.html#TutorialCodeGeneration
 */
class EDLGenerator implements IGenerator {
	
	Map<String, TypeDefinition> dataTypes;
	
	ITypeInformationProvider typing;
	
	@Inject
	IFunctionRegistry registry;
	
	override void doGenerate(Resource resource, IFileSystemAccess fsa) {
		dataTypes = new HashMap();
		
		var document = resource.contents.filter(typeof(EdlDocument)).findFirst[true];		
		var allParts = document.package.toParts()			
		
		var packageString = String.join(".", allParts);
		var path = String.join("/", allParts) + "/";				
		
		// We use null error reporting service, as there should not be any errors present during code generation - validation would not allow it
		typing = new DefaultTypeInformationProvider(new NullErrorReportingService(), document, registry);
		
		for(TypeDefinition d : document.knowledgeTypes) {
			generateType(d, fsa, path, packageString)
			generateThriftType(d, fsa, path, packageString)
			dataTypes.put(d.name, d);
		}
		
		for(DataContractDefinition d : document.dataContracts) {
			generateDataContract(d, fsa, path, packageString)
			generateThriftType(d, fsa, path, packageString)
			dataTypes.put(d.name, d);
		}
		
		for(EnsembleDefinition e : document.ensembles) {
			generateEnsemble(e, fsa, path, packageString);			
		}
	}
	
	def void generateEnsemble(EnsembleDefinition e, IFileSystemAccess fsa, String path, String packageString) {
		var generatorVisitor = new CodeGeneratorVisitor(typing, e)
		
		fsa.generateFile(path+e.name + ".java", 
			
'''package «packageString»;

import java.util.ArrayList;
import java.util.List;
import cz.cuni.mff.d3s.deeco.ensembles.EnsembleInstance;
import cz.cuni.mff.d3s.jdeeco.edl.functions.*;
import java.util.stream.*;

public class «e.name» implements EnsembleInstance {
	// Ensemble ID
	public final «EDLUtils.getJavaTypeName(e.id.type.name)» «e.id.fieldName»;
	
	public «e.name»(«EDLUtils.getJavaTypeName(e.id.type.name)» «e.id.fieldName») {
		this.«e.id.fieldName» = «e.id.fieldName»;
		«FOR r : e.roles»
		«IF r.cardinalityMax != 1»		
		«r.name» = new ArrayList<>();
		«ENDIF»		
		«ENDFOR»
	}
	
	// Aliases
	«FOR a : e.aliases»	
	public «EDLUtils.getJavaTypeName(typing.getType(a.aliasValue, e))» «a.aliasId»() {
		return «a.aliasValue.accept(generatorVisitor)»;
	}
		
	«ENDFOR»		  
	
	// Ensemble roles		
	«FOR r : e.roles»
	«IF r.cardinalityMax == 1»
	public «r.type.name» «r.name»;
	«ELSE»
	public List<«r.type.name»> «r.name»;
	«ENDIF»
	«ENDFOR»

	// Knowledge exchange

	@Override
	public void performKnowledgeExchange() {
		«FOR rule : e.exchangeRules»
		«var role = e.roles.findFirst[it.name.equals(rule.field.toParts().get(0))]»
		«IF (role.cardinalityMax != 1)»
		for («role.type.toString()» x : «role.name») {
			x.«String.join(".", rule.field.toParts().drop(1))» = «rule.query.accept(generatorVisitor)»;
		} 
		«ELSE»
		«rule.field.toString()» = «rule.query.accept(generatorVisitor)»;
		«ENDIF»				
		«ENDFOR»		
	}		
}'''
			);
	}
	
	def void generateDataContract(DataContractDefinition d, IFileSystemAccess fsa, String path, String packageString) {
		fsa.generateFile(path+d.name + ".java", 
			
'''package «packageString»;

import cz.cuni.mff.d3s.deeco.annotations.Role;
import cz.cuni.mff.d3s.jdeeco.edl.BaseDataContract;

@Role
public class «d.name» extends BaseDataContract {
	«FOR f : d.fields»					
	public «EDLUtils.getJavaTypeName(f.type.name)» «f.name»;
	«ENDFOR»				
}'''
			);
	}
	
	def void generateType(TypeDefinition d, IFileSystemAccess fsa, String path, String packageString) {
		fsa.generateFile(path+d.name + ".java", 
			
'''package «packageString»;

public class «d.name» {	
	«FOR f : d.fields»				
	public «EDLUtils.getJavaTypeName(f.type.name)» «f.name»;
	«ENDFOR»				
}'''
			);
	}	
	
	def void generateThriftType(TypeDefinition d, IFileSystemAccess fsa, String path, String packageString) {
		var int offset = 1; 
		var int fieldCount = 1;
		fsa.generateFile(path+d.name + ".thrift",
'''namespace java «packageString»

struct «d.name» {	
	«FOR f : d.fields»
	«offset»: «getThriftTypeName(f.type.name)» «f.name»«IF (fieldCount != d.fields.length)»,«ENDIF»
	«{fieldCount++; offset++; ""}»
	«ENDFOR»				
}'''		
		);
	}
	
	def String getThriftTypeName(String type) {
		if (type.startsWith("set"))
			return "set<" + getThriftTypeName(EDLUtils.stripSet(type)) + ">"
		
		switch type {
			case PrimitiveTypes.INT:
				"i32"
			case PrimitiveTypes.FLOAT:
				"double"
			case PrimitiveTypes.STRING:
				"string"
			case PrimitiveTypes.BOOL:
				"bool"
			default:
				type
		}			
	}	
}
