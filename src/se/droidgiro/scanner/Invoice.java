/*
 * Copyright (C) 2011 DroidGiro authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package se.droidgiro.scanner;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.util.Log;

/**
 * An invoice object represents all information needed to register a payment for
 * the same. A complete invoice contains:
 * 
 * <ul>
 * <li>A reference number with a valid check digit</li>
 * <li>An amount with two decimals with a valid check digit</li>
 * <li>A BG/PG account number</li>
 * </ul>
 * Optionally, an invoice may also contain the internal document type<br/>
 * 
 * Validation is made inside the corresponding set methods. If validation fails,
 * the fields are not set. The {@code isComplete} method will only return true
 * if all mandatory fields are set.
 * 
 * <br/>
 * <br/>
 * 
 * @author pakerfeldt
 * 
 */
public class Invoice {

	private final String TAG = "DroidGiro.Invoice";

	public static final String FIELDS_FOUND = "Invoice.fieldsFound";

	public static final int REFERENCE_FIELD = 1;

	public static final int AMOUNT_FIELD = 2;

	public static final int GIRO_ACCOUNT_FIELD = 4;

	public static final int DOCUMENT_TYPE_FIELD = 8;

	public int lastFieldsDecoded = 0;

	/*
	 * The patterns below have been derived from reading the following
	 * documents:
	 * http://www.bgc.se/upload/Gemensamt/Trycksaker/Manualer/BG6070.pdf
	 * http://www
	 * .plusgirot.se/sitemod/upload/root/www_plusgirot_se/pdfer/allmanbeskrivning
	 * /g445_allman_beskrivning_inbetalningservice.pdf
	 */

	/**
	 * OCR PATTERN
	 * <ul>
	 * <li>Start of string</li>
	 * <li>followed by H, one or more spaces and # OR</li>
	 * <li>&nbsp;&nbsp;&nbsp;&nbsp;followed by an optional # and one or more
	 * spaces
	 * <em>(optional because the user might not have included the # left to the OCR
	 * number)</em></li>
	 * <li>followed by 2 to 25 digits <em>(the OCR number)</em></li>
	 * <li>followed by one to three spaces,
	 * <li>followed by a #</li>
	 * <li>followed by one or more spaces (this means you MUST include at least
	 * one space after the OCR reference!)</li>
	 * <li><strong>NOT</strong> followed by two digits and #
	 * <em>(because in that case we have read the BG/PG
	 * account number)</em></li>
	 * </ul>
	 * Result: <br/>
	 * <ul>
	 * <li>group 1 - Not used</li>
	 * <li>group 2 - Complete OCR number (including OCR check digit)</li>
	 * <li>group 3 - OCR check digit</li>
	 * </ul>
	 */
	private static final Pattern OCR_PATTERN = Pattern
			.compile("^(H\\s+#|#?\\s+)(\\d{1,24}(\\d))\\s{1,3}#\\s+(?!\\d{2}#)");

	/**
	 * AMOUNT PATTERN
	 * <ul>
	 * <li>Start of string and zero or more spaces OR</li>
	 * <li>&nbsp;&nbsp;&nbsp;&nbsp;# and one or more spaces</li>
	 * <li>followed by 1 to 8 digits <em>(the
	 * amount)</li>
	 * <li>followed by one or more spaces</li>
	 * <li> followed by 2 digits (�re, the fractional part of total amount)</em></li>
	 * <li>followed by one to three spaces</li>
	 * <li>followed by 1 digit <em>(check)</em></li>
	 * <li>followed by zero or more spaces</li>
	 * <li>followed by a ></li>
	 * </ul>
	 * Result:<br/>
	 * <ul>
	 * <li>group 1 - Not used</li>
	 * <li>group 2 - Amount in whole SEK</li>
	 * <li>group 3 - Amount in �re (fractional part)</li>
	 * <li>group 4 - Amount check digit</li>
	 * </ul>
	 */
	private static final Pattern AMOUNT_PATTERN = Pattern
			.compile("(^\\s*|#\\s+)(\\d{1,8})\\s+(\\d{2})\\s{1,3}(\\d)\\s>");

	/**
	 * BG/PG NUMBER PATTERN<br/>
	 * <ul>
	 * <li>Start of string and zero or more spaces OR</li>
	 * <li>&nbsp;&nbsp;&nbsp;&nbsp;> and one or more spaces</li>
	 * <li>followed by 7 to 8 digits <em>(the
	 * BG/PG account number)</em></li>
	 * <li>followed by an optional space</li>
	 * <li>followed by a #</li>
	 * <li>followed by 2 digits <em>(the internal
	 * document type)</em></li>
	 * <li>followed by a #</li>
	 * <li>followed by zero or more spaces</li>
	 * <li>followed by end of string</li>
	 * </ul>
	 * 
	 * Result:<br/>
	 * <ul>
	 * <li>group 1 - Not used</li>
	 * <li>group 2 - BG/PG number</li>
	 * <li>group 3 - Internal document type</li>
	 * </ul>
	 */
	private static final Pattern ACCOUNT_PATTERN = Pattern
			.compile("(^\\s*|>\\s+)(\\d{7,8})\\s?#(\\d{2})#\\s*$");

	private String reference;

	private int amount = -1;

	private short amountFractional = -1;

	private String checkDigitAmount;

	private String giroAccount;

	private short internalDocumentType;

	public String getReference() {
		return reference;
	}

	public void initReference() {
		reference = null;
	}

	public short getCheckDigitReference() {
		return Short.parseShort(reference.substring(reference.length() - 1));
	}

	public void initAmount() {
		amount = -1;
		amountFractional = -1;
	}

	public int getAmount() {
		return amount;
	}

	public short getAmountFractional() {
		return amountFractional;
	}

	public String getCompleteAmount() {
		if (amount != -1 && amountFractional != -1) {
			return Integer.toString(amount)
					+ ","
					+ (amountFractional < 10 ? "0" + amountFractional
							: amountFractional);
		} else {
			return "";
		}
	}

	public String getCheckDigitAmount() {
		return checkDigitAmount;
	}

	public void initGiroAccount() {
		giroAccount = null;
	}

	/**
	 * Will try to figure out whether this invoice is a PlusGiro or BankGiro
	 * invoice by looking at the internal document type. Until we see some
	 * describing documents we will have to go for empirical studies.
	 * 
	 * @return null if the internal document type equals -1, "BG" if it equals
	 *         41 or 42 otherwise "PG"
	 */
	public String getType() {
		if (internalDocumentType == -1)
			return null;
		if (internalDocumentType == 41 || internalDocumentType == 42)
			return "BG";
		else
			return "PG";
	}

	public String getGiroAccount() {
		if (giroAccount == null)
			return null;

		String type = getType();
		if ("BG".equals(type)) {
			if (giroAccount.length() == 8)
				return giroAccount.substring(0, 4) + "-"
						+ giroAccount.substring(4);
			else if (giroAccount.length() == 7)
				return giroAccount.substring(0, 3) + "-"
						+ giroAccount.substring(3);
			else
				/* Don't know how to handle, can this even occur? */
				return giroAccount;
		} else if ("PG".equals(type)) {
			/*
			 * Naive approach, guessing this is a plusgiro number with format
			 * XXXXXX-X
			 */
			return giroAccount.substring(0, giroAccount.length() - 1) + "-"
					+ giroAccount.substring(giroAccount.length() - 1);
		} else
			return giroAccount;
	}

	public String getRawGiroAccount() {
		return giroAccount;
	}

	public void initDocumentType() {
		internalDocumentType = -1;
	}

	public short getInternalDocumentType() {
		return internalDocumentType;
	}

	public void initFields() {
		initReference();
		initAmount();
		initGiroAccount();
		initDocumentType();
	}

	public boolean isReferenceDefined() {
		return reference != null;
	}

	public boolean isAmountDefined() {
		return amount != -1 && amountFractional != -1;
	}

	public boolean isGiroAccountDefined() {
		return giroAccount != null;
	}

	public boolean isDocumentTypeDefined() {
		return internalDocumentType != -1;
	}

	/**
	 * An invoice is considered complete if it contains a reference number,
	 * amount including fractionals, the amount check digit and a giro account.
	 * 
	 * @return true if the invoice is considered complete, otherwise false
	 */
	public boolean isComplete() {
		return reference != null && amount != -1 && amountFractional != -1
				&& checkDigitAmount != null && giroAccount != null;
	}

	/**
	 * Returns all fields found in last decode.
	 * 
	 * @return all fields found in last decode.
	 */
	public int getLastFieldsDecoded() {
		return lastFieldsDecoded;
	}

	/**
	 * Parses the specified input and looks for known fields.
	 * 
	 * @param input
	 *            the {@code String} to parse for invoice fields.
	 * @return the fields found in the specified input. A field is not
	 *         considered "found" if its value has already been read.
	 */
	public int parse(String input) {
		Log.v(TAG, "Parsing " + input);
		int fieldsDecoded = 0;
		/* Look for reference number */
		Matcher m = OCR_PATTERN.matcher(input);
		if (m.find()) {
			if (isValidCC(m.group(2))) {
				if (!m.group(2).equals(reference)) {
					reference = m.group(2);
					fieldsDecoded += REFERENCE_FIELD;
				}
			}
		}

		/* Look for amount */
		m = AMOUNT_PATTERN.matcher(input);
		if (m.find()) {
			if (isValidCC(m.group(2) + m.group(3) + m.group(4))) {
				Log.v(TAG, "Got amount. Check digit valid.");
				if (!(Integer.parseInt(m.group(2)) == amount
						&& Short.parseShort(m.group(3)) == amountFractional && m
						.group(4).equals(checkDigitAmount))) {
					amount = Integer.parseInt(m.group(2));
					amountFractional = Short.parseShort(m.group(3));
					checkDigitAmount = m.group(4);
					fieldsDecoded += AMOUNT_FIELD;
				}
			} else
				Log.e(TAG, "Got amount. Check digit invalid.");
		}

		/* Look for BG/PG number */
		m = ACCOUNT_PATTERN.matcher(input);
		if (m.find()) {
			if (!(m.group(2).equals(giroAccount) && Short
					.parseShort(m.group(3)) == internalDocumentType)) {
				giroAccount = m.group(2);
				internalDocumentType = Short.parseShort(m.group(3));
				fieldsDecoded += GIRO_ACCOUNT_FIELD + DOCUMENT_TYPE_FIELD;
			}
		}
		lastFieldsDecoded = fieldsDecoded;
		return fieldsDecoded;
	}

	private static boolean isValidCC(String num) {

		final int[][] sumTable = { { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 },
				{ 0, 2, 4, 6, 8, 1, 3, 5, 7, 9 } };
		int sum = 0, flip = 0;

		for (int i = num.length() - 1; i >= 0; i--)
			sum += sumTable[flip++ & 0x1][Character.digit(num.charAt(i), 10)];
		return sum % 10 == 0;
	}

	public String toString() {
		/*
		 * Sadly, the following toString() method gets messed up when auto
		 * formatting. It returns a String which looks in some way like the
		 * bottom OCR line on a PG/BG invoice.
		 */
		return "#\t"
				+ (reference != null ? reference : "NO REF")
				+ " #\t "
				+ (amount != -1 ? "" + amount : "NO AMOUNT")
				+ " "
				+ (amountFractional != -1 ? (amountFractional < 10 ? "0"
						+ amountFractional : "" + amountFractional) : "XX")
				+ "   " + (checkDigitAmount != null ? checkDigitAmount : "X")
				+ " >\t\t" + (giroAccount != null ? giroAccount : "NO GIRO")
				+ "#"
				+ (internalDocumentType != -1 ? internalDocumentType : "XX")
				+ "#\t"
				+ (isComplete() ? "Invoice complete" : "Invoice incomplete");
	}

	public void setAmount(int amount, short amountFractional) {
		this.amount = amount;
		this.amountFractional = amountFractional;
	}

	public void setDocumentType(short documentType) {
		this.internalDocumentType = documentType;
	}

	public void setRawGiroAccount(String giroAccount) {
		this.giroAccount = giroAccount;
	}

	public void setReference(String reference) {
		this.reference = reference;
	}

}
