package com.meeple.memo;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import cpw.mods.modlauncher.api.INameMappingService;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

public class Utils {

	public static void setAccessable(Field f) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		f.setAccessible(true);
		Field modifiersField = Field.class.getDeclaredField("modifiers");
		modifiersField.setAccessible(true);
		modifiersField.setInt(f, f.getModifiers() & ~Modifier.FINAL);
	}

	public static <T> Field getPrivateField(Class<? extends T> classToAccess, String name)
		throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		Field f = classToAccess.getDeclaredField(ObfuscationReflectionHelper.remapName(INameMappingService.Domain.FIELD, name));
		setAccessable(f);
		return f;
	}

	public static <T> Field getPrivateFieldOrNull(Class<? extends T> classToAccess, String name) {
		Field f = null;
		try {
			f = getPrivateField(classToAccess, name);
		} catch (NoSuchFieldException err) {
			MemoTreetops.LOGGER.error("Field could not be found. ");
			MemoTreetops.LOGGER.catching(err);
		} catch (SecurityException err) {
			MemoTreetops.LOGGER.error("Security access violation while accessing field. ");
			MemoTreetops.LOGGER.catching(err);
		} catch (IllegalArgumentException err) {
			MemoTreetops.LOGGER.error("Invalid arguments for field. ");
			MemoTreetops.LOGGER.catching(err);
		} catch (IllegalAccessException err) {
			MemoTreetops.LOGGER.error("Illegal access while accessing field. ");
			MemoTreetops.LOGGER.catching(err);
		}

		return f;

	}

	/**
	 * Handy helper function that uses the forge obfuscation relfection helper to get a field value
	 * @param <T>
	 * @param classToAccess
	 * @param instance
	 * @param name
	 * @return the object
	 */
	@SuppressWarnings("unchecked")
	public static <T, E> E getPrivateValue(Class<? extends T> classToAccess, T instance, String name) {
		Object ret = null;
		try {
			Field f = getPrivateField(classToAccess, name);
			ret = f.get(instance);
		} catch (Exception e) {
			MemoTreetops.LOGGER.catching(e);
		}
		return (E) ret;
	}

	/**
	 * Handy helper function that uses the forge obfuscation relfection helper but allows fields to be set even with final properties
	 * @param <T>
	 * @param classToAccess
	 * @param instance
	 * @param name
	 * @return
	 */
	public static <T, E> void setPrivateValue(Class<? super T> classToAccess, T instance, E value, String fieldName) {
		try {
			Field f = getPrivateField(classToAccess, fieldName);
			f.set(instance, value);
		} catch (Exception e) {
			MemoTreetops.LOGGER.catching(e);
		}

	}
}
