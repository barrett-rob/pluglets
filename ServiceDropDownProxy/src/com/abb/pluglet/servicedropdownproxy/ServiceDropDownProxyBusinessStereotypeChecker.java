package com.abb.pluglet.servicedropdownproxy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

import javax.management.OperationsException;

import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.uml2.uml.DirectedRelationship;
import org.eclipse.uml2.uml.Element;
import org.eclipse.uml2.uml.InterfaceRealization;
import org.eclipse.uml2.uml.NamedElement;
import org.eclipse.uml2.uml.Operation;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.Parameter;
import org.eclipse.uml2.uml.ParameterDirectionKind;
import org.eclipse.uml2.uml.Property;
import org.eclipse.uml2.uml.Stereotype;
import org.eclipse.uml2.uml.Type;
import org.eclipse.uml2.uml.internal.impl.ClassImpl;
import org.eclipse.uml2.uml.internal.impl.CollaborationImpl;
import org.eclipse.uml2.uml.internal.impl.DependencyImpl;
import org.eclipse.uml2.uml.internal.impl.ModelImpl;
import org.eclipse.uml2.uml.internal.impl.OperationImpl;

import com.ibm.xtools.modeler.ui.UMLModeler;
import com.ibm.xtools.pluglets.Pluglet;

public class ServiceDropDownProxyBusinessStereotypeChecker extends Pluglet {

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
									for (OperationImpl oi : operations) {
										i = replace(i, oi);
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

	private int replace(int i, OperationImpl oi) {
		out.println("apply business stereotype to: " + oi);
		Stereotype bst = oi.getApplicableStereotype("Services::Business");
		oi.applyStereotype(bst);
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
		if (e instanceof ClassImpl) {
			process((ClassImpl) e);
		} else {
			return;
		}
	}

	private void process(ClassImpl ci) {
		for (Operation o : ci.getOperations()) {
			process((OperationImpl) o);
		}
	}

	private final List<OperationImpl> operations = new ArrayList<OperationImpl>();

	private void process(OperationImpl oi) {
		if (!oi.getName().endsWith("ForDropdown")) {
			return;
		}
		out.println("considering: " + oi);
		Stereotype bst = oi.getAppliedStereotype("Services::Business");
		if (bst == null) {
			if (!operations.contains(oi)) {
				operations.add(oi);
			}
		} else {
			out.println("already has business stereotype...");
		}
	}

}
