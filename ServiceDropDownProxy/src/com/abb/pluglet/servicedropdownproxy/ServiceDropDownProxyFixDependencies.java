package com.abb.pluglet.servicedropdownproxy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.uml2.uml.DirectedRelationship;
import org.eclipse.uml2.uml.Element;
import org.eclipse.uml2.uml.Operation;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.Stereotype;
import org.eclipse.uml2.uml.internal.impl.ClassImpl;
import org.eclipse.uml2.uml.internal.impl.DependencyImpl;
import org.eclipse.uml2.uml.internal.impl.ModelImpl;
import org.eclipse.uml2.uml.internal.impl.OperationImpl;

import com.ibm.xtools.modeler.ui.UMLModeler;
import com.ibm.xtools.pluglets.Pluglet;

public class ServiceDropDownProxyFixDependencies extends Pluglet {

	public void plugletmain(String[] args) {
		try {
			UMLModeler.getEditingDomain().runExclusive(new Runnable() {
				public void run() {
					for (Iterator<EObject> iter = UMLModeler.getUMLUIHelper()
							.getSelectedElements().iterator(); iter.hasNext();) {
						Object o = iter.next();
						process((ModelImpl) o);
						out.println(o);
					}
					TransactionalEditingDomain domain = UMLModeler
							.getEditingDomain();
					domain.getCommandStack().execute(
							new RecordingCommand(domain,
									"create new service dropdowns") {
								@Override
								protected void doExecute() {
									int i = 0;
									List<String> keys = new ArrayList<String>(
											operations.keySet());
									Collections.sort(keys);
									for (String key : keys) {
										OperationImpl oi = operations.get(key);
										i = replace(i, oi,
												dependencies.get(oi),
												getNewName(oi.getName()));
									}
									// if (1 == 1)
									// throw new RuntimeException("stop");
								}
							});
				}
			});

		} catch (InterruptedException e) {
			out.println("The operation was interrupted");
		}
	}

	private String getNewName(String name) {
		StringBuilder sb = new StringBuilder(name);
		sb.append("ForDropdown");
		return sb.toString();
	}

	private int replace(int i, OperationImpl oi, List<DependencyImpl> dis,
			String newName) {
		ClassImpl service = getService(oi);
		out.println("looking for operation: " + service.getName() + ":"
				+ newName);
		Operation newOperation = null;
		for (Operation o : service.getOperations()) {
			out.print(o.getName() + ", ");
			if (o.getName().equals(newName)) {
				newOperation = o;
			}
		}
		out.println();
		if (newOperation == null) {
			throw new RuntimeException("unexpected");
		}
		out.println(i + ":\t" + service.getName() + ":\t" + oi.getName()
				+ "() -> " + dis.size() + " usages, replacing with " + newName
				+ "()");
		return i + 1;
	}

	private String capitalise(String s) {
		StringBuilder sb = new StringBuilder();
		sb.append(s.substring(0, 1).toUpperCase());
		sb.append(s.substring(1));
		return sb.toString();
	}

	private void process(ModelImpl mi) {
		for (Package p : mi.getNestedPackages()) {
			process(p);
		}
	}

	private void process(Package p) {
		for (Element e : p.getOwnedElements()) {
			if (e instanceof Package) {
				process((Package) e);
			} else {
				process(e);
			}
		}
	}

	private void process(Element e) {
		if (e instanceof DependencyImpl) {
			process((DependencyImpl) e);
		} else {
			return;
		}
	}

	private void process(DependencyImpl di) {
		EList<Element> ts = di.getTargets();
		for (Element t : ts) {
			for (Stereotype st : t.getAppliedStereotypes()) {
				if ("ServiceDropdown".equals(st.getName())) {
					processServiceDropdown(t);
				}
			}
		}
	}

	private void processServiceDropdown(Element serviceDropdown) {
		for (DirectedRelationship dr : serviceDropdown
				.getSourceDirectedRelationships()) {
			if (dr instanceof DependencyImpl) {
				DependencyImpl di = (DependencyImpl) dr;
				EList<Element> ts = di.getTargets();
				for (Element t : ts) {
					for (Stereotype st : t.getAppliedStereotypes()) {
						if ("Search".equals(st.getName())) {
							OperationImpl oi = (OperationImpl) t;
							if (t instanceof OperationImpl) {
								processOperationImpl(di, oi);
							}
						}
					}
				}
			}
		}
	}

	private final IdentityHashMap<OperationImpl, List<DependencyImpl>> dependencies = new IdentityHashMap<OperationImpl, List<DependencyImpl>>();
	private final LinkedHashMap<String, OperationImpl> operations = new LinkedHashMap<String, OperationImpl>();

	private void processOperationImpl(DependencyImpl di, OperationImpl oi) {
		String name = oi.getName();
		if (name.toLowerCase().contains("dropdown")) {
			return;
		}
		ClassImpl service = getService(oi);
		if (!service.getModel().getName().startsWith("Module-2")) {
			return;
		}
		if (!operations.containsKey(oi)) {
			dependencies.put(oi, new ArrayList<DependencyImpl>());
		}
		dependencies.get(oi).add(di);
		operations.put(service.getName() + ":" + name, oi);
	}

	private ClassImpl getService(OperationImpl oi) {
		return (ClassImpl) oi.getOwner();
	}

}
