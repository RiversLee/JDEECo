package cz.cuni.mff.d3s.deeco.processor;

import java.io.File;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;

public class JarClassUtils {
	public static List<String> getClassNamesFromJar(String jarPath) {
		try {
			List<String> result = new LinkedList<String>();
			JarFile jarFile = null;
			try {
				jarFile = new JarFile(jarPath);
				Enumeration<JarEntry> enumeration = jarFile.entries();
				JarEntry je;
				JavaClass jc;
				while (enumeration.hasMoreElements()) {
					je = enumeration.nextElement();
					if (je.getName().endsWith(".class")) {
						try {
							jc = new ClassParser(jarPath, je.getName()).parse();
							result.add(jc.getClassName());
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
			} finally {
				if (jarFile != null)
					jarFile.close();
			}
			return result;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public static List<String> getClassNamesFromDir(File dir) {
		List<String> result = new LinkedList<String>();
		ClassParser cp;
		if (dir != null && dir.isDirectory()) {
			File [] contents = dir.listFiles();
			if (contents != null && contents.length > 0) {
				FileExtensionFilter classFef = new FileExtensionFilter(".class");
				for (File f : contents) {
					if (f.isDirectory())
						result.addAll(getClassNamesFromDir(f));
					else if (classFef.accept(f.getParentFile(), f.getName())) {
						try {
							cp = new ClassParser(f.getAbsolutePath());
							result.add(cp.parse().getClassName());
							System.out.println("File found: " + f.getName());
						} catch (Exception e) {	
						}
					}
				}
			}
		}
		return result;
	}
}