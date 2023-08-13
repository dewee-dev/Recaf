package software.coley.recaf.services.cell.builtin;

import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import javafx.collections.ObservableList;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import org.kordamp.ikonli.carbonicons.CarbonIcons;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import software.coley.recaf.analytics.logging.Logging;
import software.coley.recaf.info.ClassInfo;
import software.coley.recaf.info.JvmClassInfo;
import software.coley.recaf.info.annotation.Annotated;
import software.coley.recaf.info.annotation.AnnotationInfo;
import software.coley.recaf.info.builder.JvmClassInfoBuilder;
import software.coley.recaf.info.member.ClassMember;
import software.coley.recaf.info.member.FieldMember;
import software.coley.recaf.info.member.MethodMember;
import software.coley.recaf.path.ClassPathNode;
import software.coley.recaf.services.cell.*;
import software.coley.recaf.services.navigation.Actions;
import software.coley.recaf.util.visitors.ClassAnnotationRemovingVisitor;
import software.coley.recaf.util.visitors.FieldAnnotationRemovingVisitor;
import software.coley.recaf.util.visitors.MethodAnnotationRemovingVisitor;
import software.coley.recaf.workspace.model.Workspace;
import software.coley.recaf.workspace.model.bundle.ClassBundle;
import software.coley.recaf.workspace.model.resource.WorkspaceResource;

import static software.coley.recaf.util.Menus.action;
import static software.coley.recaf.util.Unchecked.cast;
import static software.coley.recaf.util.Unchecked.runnable;

/**
 * Basic implementation for {@link AnnotationContextMenuProviderFactory}.
 *
 * @author Matt Coley
 */
@ApplicationScoped
public class BasicAnnotationContextMenuProviderFactory extends AbstractContextMenuProviderFactory
		implements AnnotationContextMenuProviderFactory {
	private static final Logger logger = Logging.get(BasicAnnotationContextMenuProviderFactory.class);

	@Inject
	public BasicAnnotationContextMenuProviderFactory(@Nonnull TextProviderService textService,
													 @Nonnull IconProviderService iconService,
													 @Nonnull Actions actions) {
		super(textService, iconService, actions);
	}

	@Nonnull
	@Override
	public ContextMenuProvider getAnnotationContextMenuProvider(@Nonnull ContextSource source,
																@Nonnull Workspace workspace,
																@Nonnull WorkspaceResource resource,
																@Nonnull ClassBundle<? extends ClassInfo> bundle,
																@Nonnull Annotated annotated,
																@Nonnull AnnotationInfo annotation) {
		return () -> {
			TextProvider nameProvider = textService.getAnnotationTextProvider(workspace, resource, bundle, annotated, annotation);
			IconProvider iconProvider = iconService.getAnnotationIconProvider(workspace, resource, bundle, annotated, annotation);
			ContextMenu menu = new ContextMenu();
			addHeader(menu, nameProvider.makeText(), iconProvider.makeIcon());

			String annotationType = Type.getType(annotation.getDescriptor()).getInternalName();

			ObservableList<MenuItem> items = menu.getItems();
			ClassPathNode annotationDecPath = workspace.findClass(annotationType);
			if (annotationDecPath != null)
				items.add(action("menu.goto.class", CarbonIcons.ARROW_RIGHT, runnable(() -> actions.gotoDeclaration(annotationDecPath))));

			items.add(action("menu.edit.remove.annotation", CarbonIcons.TRASH_CAN, () -> {
				try {
					if (annotated instanceof JvmClassInfo target) {
						ClassWriter writer = new ClassWriter(0);
						target.getClassReader().accept(new ClassAnnotationRemovingVisitor(writer, annotationType), 0);
						JvmClassInfo updatedClass = new JvmClassInfoBuilder(new ClassReader(writer.toByteArray())).build();
						bundle.put(cast(updatedClass));
					} else if (annotated instanceof ClassMember member && member.getDeclaringClass() instanceof JvmClassInfo target) {
						ClassWriter writer = new ClassWriter(0);
						if (member.isField()) {
							FieldMember field = (FieldMember) member;
							target.getClassReader().accept(FieldAnnotationRemovingVisitor.forClass(writer, annotationType, field), 0);
						} else {
							MethodMember method = (MethodMember) member;
							target.getClassReader().accept(MethodAnnotationRemovingVisitor.forClass(writer, annotationType, method), 0);
						}
						JvmClassInfo updatedClass = new JvmClassInfoBuilder(new ClassReader(writer.toByteArray())).build();
						bundle.put(cast(updatedClass));
					}
				} catch (Throwable t) {
					logger.error("Failed removing annotation", t);
				}
			}));

			return menu;
		};
	}
}
