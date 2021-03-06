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

public class ServiceDropDownProxy extends Pluglet {

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
									for (OperationImpl oi : dependencies
											.keySet()) {
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
		// orchestrator
		String oiOrchestratorName = service.getName()
				+ capitalise(oi.getName()) + "Orchestrator";
		ClassImpl oiOrchestrator = (ClassImpl) service
				.getOwnedMember(oiOrchestratorName);
		if (oiOrchestrator == null) {
			out.println("EE *** no orchestrator found for " + service.getName()
					+ ":" + oi.getName());
			return i;
		}
		ClassImpl copyOrchestrator = (ClassImpl) EcoreUtil.copy(oiOrchestrator);
		copyOrchestrator.setName(service.getName() + capitalise(newName)
				+ "Orchestrator");
		if (service.getNestedClassifier(copyOrchestrator.getName()) != null) {
			out.println("EE *** orchestrator already exists: "
					+ copyOrchestrator.getName());
		}
		copyOrchestrator = (ClassImpl) service.createNestedClassifier(
				copyOrchestrator.getName(), copyOrchestrator.eClass());
		for (InterfaceRealization ir : oiOrchestrator
				.getInterfaceRealizations()) {
			copyOrchestrator.createInterfaceRealization(ir.getName(), ir
					.getContract());
		}
		// collaboration
		String oiCollaborationName = service.getName()
				+ capitalise(oi.getName()) + "Sequence";
		CollaborationImpl oiCollaboration = (CollaborationImpl) service
				.getOwnedMember(oiCollaborationName);
		if (oiOrchestrator == null) {
			out.println("EE *** no collaboration found for "
					+ service.getName() + ":" + oi.getName());
			return i;
		}
		CollaborationImpl copyCollaboration = (CollaborationImpl) EcoreUtil
				.copy(oiCollaboration);
		copyCollaboration.setName(service.getName() + capitalise(newName)
				+ "Sequence");
		copyCollaboration = (CollaborationImpl) service.createNestedClassifier(
				copyCollaboration.getName(), copyCollaboration.eClass());
		for (Property p : oiCollaboration.getOwnedAttributes()) {
			Property created = copyCollaboration.createOwnedAttribute(p
					.getName(), p.getType());
			if (created.getType() != null
					&& created.getType().equals(oiOrchestrator)) {
				created.setType(copyOrchestrator);
			}
		}
		// create new operation
		Parameter oiResult = oi.getReturnResult();
		EList<String> names = new BasicEList<String>();
		EList<Type> types = new BasicEList<Type>();
		for (Parameter p : oi.getOwnedParameters()) {
			if (oiResult.getType().equals(p.getType())) {
				continue;
			}
			names.add(p.getName());
			types.add(p.getType());
		}
		Operation copy = service.createOwnedOperation(newName, names, types);
		Parameter copyReturnResult = copy.createReturnResult(
				oiResult.getName(), oiResult.getType());
		copyReturnResult.setDirection(ParameterDirectionKind.RETURN_LITERAL);
		copyReturnResult.setLower(oiResult.getLower());
		copyReturnResult.setUpper(oiResult.getUpper());
		for (Stereotype st : oi.getAppliedStereotypes()) {
			copy.applyStereotype(st);
		}
		// skip security
		Stereotype operationSecurity = copy
				.getAppliedStereotype("Services::OperationSecurity");
		if (operationSecurity == null) {
			operationSecurity = copy
					.getApplicableStereotype("Services::OperationSecurity");
			copy.applyStereotype(operationSecurity);
		}
		copy.setValue(operationSecurity, "skipSecurity", true);
		Stereotype business = copy.getAppliedStereotype("Services::Business");
		if (business == null) {
			business = copy.getApplicableStereotype("Services::Business");
			copy.applyStereotype(business);
		}
		// re-point references from old to new
		IdentityHashMap<NamedElement, Object> clients = new IdentityHashMap<NamedElement, Object>();
		IdentityHashMap<NamedElement, Object> suppliers = new IdentityHashMap<NamedElement, Object>();
		for (DependencyImpl di : dis) {
			for (NamedElement ne : di.getClients()) {
				clients.put(ne, "");
			}
			for (NamedElement ne : di.getSuppliers()) {
				suppliers.put(ne, "");
			}
		}
		out.println("\t[" + clients.size() + "] clients: " + clients.keySet());
		out.println("\t[" + suppliers.size() + "] suppliers: "
				+ suppliers.keySet());
		if (suppliers.size() != 1) {
			throw new IllegalStateException();
		}
		for (DependencyImpl di : dis) {
			di.getSuppliers().clear();
			di.getSuppliers().add(copy);
		}
		i += 1;
		out.println(i + ":\t" + service.getName() + ":\t" + oi.getName()
				+ "() -> " + dis.size() + " usages, replacing with " + newName
				+ "()");
		return i;
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

	private void processOperationImpl(DependencyImpl di, OperationImpl oi) {
		String name = oi.getName();
		if (name.toLowerCase().contains("dropdown")) {
			return;
		}
		ClassImpl service = getService(oi);
		if (!service.getModel().getName().startsWith("Module-2")) {
			return;
		}
		if (!dependencies.containsKey(oi)) {
			dependencies.put(oi, new ArrayList<DependencyImpl>());
		}
		dependencies.get(oi).add(di);
	}

	private ClassImpl getService(OperationImpl oi) {
		return (ClassImpl) oi.getOwner();
	}

}
