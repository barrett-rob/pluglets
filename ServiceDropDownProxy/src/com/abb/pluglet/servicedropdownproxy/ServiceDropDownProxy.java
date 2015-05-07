package com.abb.pluglet.servicedropdownproxy;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;

import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.transaction.RecordingCommand;
import org.eclipse.emf.transaction.TransactionalEditingDomain;
import org.eclipse.uml2.uml.DirectedRelationship;
import org.eclipse.uml2.uml.Element;
import org.eclipse.uml2.uml.NamedElement;
import org.eclipse.uml2.uml.Operation;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.Parameter;
import org.eclipse.uml2.uml.ParameterDirectionKind;
import org.eclipse.uml2.uml.Stereotype;
import org.eclipse.uml2.uml.Type;
import org.eclipse.uml2.uml.internal.impl.ClassImpl;
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
									for (OperationImpl oi : operations.keySet()) {
										String oldName = oi.getName();
										String newName = getNewName(oldName);
										NamedElement service = getService(oi);
										out.println(++i + ":\t"
												+ service.getName() + ":\t"
												+ oldName + "() -> "
												+ operations.get(oi).size()
												+ " usages, replacing with "
												+ newName + "()");
										replace(oi, operations.get(oi), newName);
									}
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

	private void replace(OperationImpl oi, List<DependencyImpl> dis,
			String newName) {
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
		ClassImpl service = getService(oi);
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

	private final IdentityHashMap<OperationImpl, List<DependencyImpl>> operations = new IdentityHashMap<OperationImpl, List<DependencyImpl>>();

	private void processOperationImpl(DependencyImpl di, OperationImpl oi) {
		String name = oi.getName();
		if (name.toLowerCase().contains("dropdown")) {
			return;
		}
		if (oi.getModel().getName().toLowerCase().matches("m2\\d\\d\\d")) {
			return;
		}
		if (!operations.containsKey(oi)) {
			operations.put(oi, new ArrayList<DependencyImpl>());
		}
		operations.get(oi).add(di);
	}

	private ClassImpl getService(OperationImpl oi) {
		return (ClassImpl) oi.getOwner();
	}

}
