package me.coley.recaf.ui.control.tree.item;

import javafx.beans.property.SimpleStringProperty;
import me.coley.recaf.code.*;
import me.coley.recaf.search.Search;
import me.coley.recaf.search.result.*;
import me.coley.recaf.workspace.Workspace;
import me.coley.recaf.workspace.resource.Resource;
import me.coley.recaf.workspace.resource.ResourceClassListener;
import me.coley.recaf.workspace.resource.ResourceDexClassListener;

import java.util.Collection;
import java.util.UUID;

/**
 * Root item for {@link me.coley.recaf.ui.pane.ResultsPane}.
 *
 * @author Matt Coley
 */
public class ResultsRootItem extends BaseTreeItem implements ResourceClassListener, ResourceDexClassListener {
	private final Workspace workspace;
	private final Search search;
	private final Collection<Result> results;

	/**
	 * Create root item.
	 *
	 * @param workspace
	 * 		Workspace to represent.
	 * @param search
	 * 		The search ran.
	 * @param results
	 * 		Search results to show as child items.
	 */
	public ResultsRootItem(Workspace workspace, Search search, Collection<Result> results) {
		this.workspace = workspace;
		this.search = search;
		this.results = results;
		init();
	}

	/**
	 * Initialize sub-items to match the workspace layout.
	 */
	public void setup() {
		// Skip if already setup
		if (!getChildren().isEmpty()) {
			return;
		}
		// create the tree hierarchy
		for (Result result : results) {
			Location location = result.getLocation();
			if (location instanceof ClassLocation) {
				ClassLocation classLocation = (ClassLocation) location;
				CommonClassInfo ownerInfo = classLocation.getContainingClass();
				BaseTreeItem ownerItem = getClass(ownerInfo);
				if (classLocation.getContainingField() != null) {
					FieldItem fieldItem = getField(ownerItem, classLocation.getContainingField());
					// Result is in a field
					if (classLocation.getContainingAnnotation() != null) {
						// Result is on an annotation applied to the field
						fieldItem.setAnnotationType(classLocation.getContainingAnnotation());
					}
				} else if (classLocation.getContainingMethod() != null) {
					boolean hasInsn = classLocation.getInstruction() != null;
					MethodItem methodItem = getMethod(ownerItem, classLocation.getContainingMethod(), hasInsn);
					// Result is in a method
					if (hasInsn) {
						// Result is on an instruction
						InsnItem insnItem = new InsnItem(classLocation.getInstruction());
						methodItem.addChild(insnItem);
					} else if (classLocation.getContainingAnnotation() != null) {
						// Result is on an annotation applied to the method
						methodItem.setAnnotationType(classLocation.getContainingAnnotation());
					}
				} else if (classLocation.getContainingAnnotation() != null) {
					// Result is on an annotation applied to the class
					((ClassItem) ownerItem).setAnnotationType(classLocation.getContainingAnnotation());
				}
			} else if (location instanceof FileLocation) {
				FileLocation fileLocation = (FileLocation) location;
				BaseTreeItem ownerItem = getFile(fileLocation.getContainingFile());
				// Show text of matched values.
				// We make a fake path for these child items so that '/' from the search results isn't confused
				// with an attempt to make further sub-items.
				String path = UUID.randomUUID().toString();
				if (result instanceof TextResult) {
					TextResult textResult = (TextResult) result;
					ownerItem.addChild(new DummyItem(path,
							new SimpleStringProperty(textResult.getMatchedText())));
				} else if (result instanceof NumberResult) {
					NumberResult numberResult = (NumberResult) result;
					ownerItem.addChild(new DummyItem(path,
							new SimpleStringProperty(numberResult.getMatchedNumber().toString())));
				}
			}
		}
	}

	private BaseTreeItem getFile(FileInfo fileInfo) {
		BaseTreeItem item = this;
		String[] pieces = fileInfo.getName().split("/");
		for (String piece : pieces) {
			if (item == null) break;
			item = item.getChildDirectory(piece);
		}
		if (!(item instanceof FileItem)) {
			item = addPath(this, fileInfo.getName(), FileItem::new, DirectoryItem::new);
		}
		return item;
	}

	private BaseTreeItem getClass(CommonClassInfo ownerInfo) {
		BaseTreeItem item = this;
		String[] pieces = ownerInfo.getName().split("/");
		for (String piece : pieces) {
			if (item == null) break;
			item = item.getChildDirectory(piece);
		}
		if (!(item instanceof ClassItem)) {
			item = addPath(this, ownerInfo.getName(), n -> new ClassItem(n, true), PackageItem::new);
		}
		return item;
	}

	private MethodItem getMethod(BaseTreeItem ownerItem, MethodInfo info, boolean hasInsn) {
		String key = info.getName() + info.getDescriptor();
		MethodItem item = (MethodItem) ownerItem.getChildFile(key);
		if (item == null) {
			item = (MethodItem) ownerItem.getChildDirectory(key);
		}
		if (item == null) {
			item = new MethodItem(info, hasInsn);
			ownerItem.addChild(item);
		}
		return item;
	}

	private FieldItem getField(BaseTreeItem ownerItem, FieldInfo info) {
		String key = info.getDescriptor() + " " + info.getName();
		FieldItem item = (FieldItem) ownerItem.getChildFile(key);
		if (item == null) {
			item = new FieldItem(info);
			ownerItem.addChild(item);
		}
		return item;
	}

	/**
	 * @return The search ran.
	 */
	public Search getSearch() {
		return search;
	}

	/**
	 * @return Search results of the {@link #getSearch() ran search}.
	 */
	public Collection<Result> getResults() {
		return results;
	}

	@Override
	protected BaseTreeValue createTreeValue() {
		return new ResultsRootValue(this);
	}

	@Override
	public boolean forceVisible() {
		return true;
	}

	@Override
	public void onNewClass(Resource resource, ClassInfo newValue) {
		// no-op
	}

	@Override
	public void onRemoveClass(Resource resource, ClassInfo oldValue) {
		remove(this, oldValue.getName());
	}

	@Override
	public void onNewDexClass(Resource resource, String dexName, DexClassInfo newValue) {
		// no-op
	}

	@Override
	public void onRemoveDexClass(Resource resource, String dexName, DexClassInfo oldValue) {
		remove(this, oldValue.getName());
	}

	@Override
	public void onUpdateClass(Resource resource, ClassInfo oldValue, ClassInfo newValue) {
		// TODO: Force Redraw?
	}

	@Override
	public void onUpdateDexClass(Resource resource, String dexName, DexClassInfo oldValue, DexClassInfo newValue) {
		// TODO: Force Redraw?
	}
}
