package de.buildacompiler.compiler;

import java.util.HashMap;



import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.*;

import de.buildacompiler.parser.*;
import de.buildacompiler.parser.WhilelangParser.*;

/**
 * Diese Klasse ist der 'Visitor', der jeden Knoten des Syntaxbaums besucht
 * und bestimmte Aktionen ausführt.
 * 
 */

public class MyVisitor extends WhilelangBaseVisitor<String> {
	private int loopCounter = 0;
	private int stackOffset = 0;
	public static int stackCapacity;
	private boolean flag = true;
	private boolean flagStack = true;

	/**
	 * Mappt Variablen auf entsprechende Register für den Zugriff zur Laufzeit.
	 */
	private Map<String, String> varToRegisterMap = new HashMap<>();
	Vector<String> variables = new Vector<String>();

	/**
	 * Um eine initiale Ausführung zu gewährleisten, wird diese Methode zu Beginn des Parsing-Prozesses aufgerufen.
	 * ein Teil wird nur ein mal durchgelaufen, um den Assembly-Code-Header zu initialisieren. 
	 */
	
	@Override
	public String visitProgram(ProgramContext ctx) {
		StringBuilder assemblyCode = new StringBuilder();
		// dieses teil wird nur einmal ausgeführt.
		if (flag == true) {

			/**
			 * Berechnet Stackkapazität  für lokale Variablen zu Beginn der Ausführung.
			 */
			calculateStackCapacity(ctx);

			
			assemblyCode.append("push rbp            ;Sichern des aktuellen Basispunkts ").append("\n");
			assemblyCode.append("mov rbp, rsp        ;Festlegen des neuen Basispunkts ").append("\n");
		
			assemblyCode.append("sub rsp, ").append(stackOffset)
			.append("         ;Reservieren von Stackspeicher für lokale Variablen\n"
					+ "\n; Initialisierung der Variablen auf dem Stack\n");
			stackCapacity = stackOffset;
			stackOffset = 0;
			varToRegisterMap.clear();
			flag = false;

		}
		for (StatementContext statement : ctx.statement()) {
			assemblyCode.append(visit(statement)).append("\n");
		}
		return assemblyCode.toString();
	}

	/**
	 *  Ermittelt und liefert den entsprechenden Ausdruck, 
	 *  abhängig von der spezifischen Operation im Kontext.
	 *  garantiert dass, das Ergebnis keine negative Zahl ist.
	 */
	
	@Override
	public String visitExpression(WhilelangParser.ExpressionContext ctx) {
		// Wenn kein '+' oder '-' Operator vorhanden ist, wird nur der Term besucht.
		if (ctx.getChildCount() == 1) {
			return visit(ctx.getChild(0));
		}
		String leftTerm = visit(ctx.getChild(0));
		String rightTerm = visit(ctx.getChild(2));
		String operator = ctx.getChild(1).getText(); 
		String result = "";
		if (flagStack == false) {
			flagStack = true;

			if (operator.equals("+")) {
				result = "\nadd rax, " + rightTerm + "                 ;Erhöht den Wert in RAX um " + rightTerm + ".\n";
			} else if (operator.equals("-")) {
				
				// Überprüft, ob der linke Term eine negative Zahl ist.
					int currentCheckzero = loopCounter++;
					return result = "\nsub rax, " + rightTerm + "                 ;Verringert den Wert in RAX um " + rightTerm + ".\n"
						+"\n; Vermeidung von negativen Werten durch das Zurücksetzen auf 0 bei Bedarf."
						+ "\ncmp rax, 0                  ;Ist der Wert ≤ 0? ."
						+ "\njg Skip_Make_Zero_"+ currentCheckzero + "         ;Falls nein, überspringen.\n" 
						+ "\nMake_Zero_" + currentCheckzero + ":"
						+ "\nmov rax, 0                  ;Falls ja, setze den Wert auf 0 "
						+ "\nSkip_Make_Zero_" + currentCheckzero + ":           ;Fortfahren mit dem nächsten Teil des Codes.\n";
					
		

			} 
		} else if (rightTerm.equals("1")) { 
			if (operator.equals("+")) {
				result = "\ninc qword " + leftTerm + "         ;Wert um 1 erhöhen.";
			} else if (operator.equals("-")) {
				result = "\ndec qword " + leftTerm + "         ;Wert um 1 verringern.";

			}
		} else {
			if (operator.equals("+")) {
				result = "\nadd qword " + leftTerm + "," + rightTerm + "        ;Erhöht den Wert in " + leftTerm
						+ " um " + rightTerm + ".";
			} else if (operator.equals("-")) {
				result = "\nsub qword " + leftTerm + "," + rightTerm + "        ;Verringert den Wert in " + leftTerm
						+ " um " + rightTerm + ".";

			}

		} 
		if (operator.equals("-")) {
			int currentCheckzero = loopCounter++;

			return result + "\n; Vermeidung von negativen Werten durch das Zurücksetzen auf 0 bei Bedarf."
			+ "\ncmp qword " + leftTerm + ", 0      ;Ist der Wert ≤ 0? ."
			+ "\njg Skip_Make_Zero_"+ currentCheckzero + "         ;Falls nein, überspringen.\n" 
			+ "\nMake_Zero_" + currentCheckzero + ":"
			+ "\nmov qword " + leftTerm + ", 0      ;Falls ja, setze den Wert auf 0 "
			+ "\nSkip_Make_Zero_" + currentCheckzero + ":           ;Fortfahren mit dem nächsten Teil des Codes.\n";
		} 
		
		
		
		else {
			return result;

		}
	}

	/**
	 * Diese Methode kümmert sich um die Zuweisung von Werten zu Variablen und stellt sicher, dass alle Variablen
      vor der Verwendung definiert werden.
	 */
	@Override
	public String visitAssignment(WhilelangParser.AssignmentContext ctx) {
		String varName = ctx.VARIABLE().getText();
		String result = "";
		if (!varToRegisterMap.containsKey(varName)) {

			assignVarToRegister(varName);
		}
		String exprValue = ctx.expression().getText();
		
		 
		if (ctx.getChild(2).getChildCount() == 1) { 

			if (isInt(exprValue)) {
				return "mov qword " + varToRegisterMap.get(varName) + ", " + visit(ctx.getChild(2))
				+ "      ;Initialisiert " + varName + " auf den Wert " + visit(ctx.getChild(2));
			} else {
				if (!varToRegisterMap.containsKey(exprValue)) {
					return "Eingabefehler: Die Variable '" + exprValue + "' ist nicht definiert.\n";

				}
				return "Eingabefehler: Der Ausdruck '" + ctx.getText() + ";' ist nicht erlaubt.\n"
						+ "Bitte verwenden Sie stattdessen eine zulässige Form, '"+ ctx.getText() +"+0;'.\n";
			}

		} else if (ctx.getChild(2).getChildCount() == 3) {
			// um sicher zu sein dass ist nicht wie x=x+1;
			if (!visit(ctx.getChild(2).getChild(0)).equals(varToRegisterMap.get(varName))) {
				if (isInt(visit(ctx.getChild(2).getChild(0))) || isInt(visit(ctx.getChild(2).getChild(2)))) {
					if(!isInt(visit(ctx.getChild(2).getChild(2)))){
						if (!varToRegisterMap.containsKey((ctx.getChild(2).getChild(2).getText()))) 
							return "Eingabefehler: Die Variable '" + (ctx.getChild(2).getChild(2).getText()) + "' ist nicht definiert.\n";
						 
					}
					if(!isInt(visit(ctx.getChild(2).getChild(0)))){
						if (!varToRegisterMap.containsKey((ctx.getChild(2).getChild(0).getText()))) 
							return "Eingabefehler: Die Variable '" + (ctx.getChild(2).getChild(0).getText()) + "' ist nicht definiert.\n";
						
					} 
					flagStack = false;
					return "; Dieser Block transferiert Daten zwischen Speicher und Registern.\n" + "mov rax, "
					+ visit(ctx.getChild(2).getChild(0)) + "             ;Holt den Wert aus dem Speicher"
					+ visit(ctx.getChild(2)) + "\n" + "mov " + varToRegisterMap.get(varName) + ", rax       ;Speichert den Wert zurück im Speicher.\n";
				}
				 

			} else if (isInt(visit(ctx.getChild(2).getChild(2)))) {
				return visit(ctx.expression());
				 
			    
		}
			}
		
		 return "Eingabefehler: Der Ausdruck '" + ctx.getText() + "' ist nicht zulässig.\n"
				+ " Bitte überprüfen Sie die Syntax in der Dokumentation.";

	}
	
	/**
	 * Diese Methode steuert die "WHILE"-Schleife. 
	 * Sie erstellt spezielle Marken für Sprünge und kümmert sich um die Regeln der Schleife. 
	 * Ihr Job ist es, die Schritte der Schleife richtig zu lenken, basierend auf den festgelegten Regeln, und zu überprüfen, dass alles zur passenden Zeit passiert.
	 */

	@Override
	public String visitWhileDoLoop(WhilelangParser.WhileDoLoopContext ctx) {
		int currentLoop = loopCounter++;
		String startLabel = "Whileloop_start_" + currentLoop;
		String endLabel = "Whileloop_end_" + currentLoop;
		String operator = ctx.getChild(2).getChild(1).getText();
		String whileOp = "";
		switch (operator) {
		case ">":
			whileOp = "jle ";
			break;
		case "<":
			whileOp = "jge ";
			break;
		case "==":
			whileOp = "jne ";
			break;
		case ">=":
			whileOp = "jl ";
			break;
		case "<=":
			whileOp = "jg ";
			break;
		default:
			whileOp = " Unbekanter operator: " + operator;
			break;
		}

		StringBuilder assemblyCode = new StringBuilder();

		assemblyCode.append("\n; Beginn einer WHILE-Schleifenstruktur.").append("\n");
		assemblyCode.append(startLabel).append(":\n");

		assemblyCode.append(visit(ctx.condition())).append("\n");

		assemblyCode.append(whileOp).append(endLabel)
		.append("         ;Springt zum Ende, wenn die Bedingung erfüllt ist.").append("\n");

		assemblyCode.append(visit(ctx.program())).append("\n");

		assemblyCode.append("jmp ").append(startLabel)
		.append("       ;Wiederholt Schleife, wenn die Bedingung nicht erfüllt ist.").append("\n");

		assemblyCode.append(endLabel).append(":").append("            ;Ende der LOOP-Schleife.").append("\n");

		return assemblyCode.toString();
	}
	
	/**
	 *   Diese Methode ermittelt die Vergleichsbefehle für die 'while'-Schleife und stellt sicher, dass die Variablen in der Bedingung bereits definiert sind. 
	 *   Sie liefert die notwendigen Anweisungen zurück, um die Bedingung der Schleife korrekt zu überprüfen.
	 */

	@Override
	public String visitCondition(ConditionContext ctx) {
		String leftVar = ctx.VARIABLE().getText();
		String rightVar = ctx.CONSTANT().getText();
		String operator = ctx.getChild(1).getText();
		String leftStackPosition = varToRegisterMap.get(leftVar);
		String rightValue = "";
		if (varToRegisterMap.containsKey(leftVar)) {
			rightValue = varToRegisterMap.get(leftVar);
		} else { 
 
			rightValue = leftVar;
			if (!rightValue.chars().allMatch(Character::isDigit)) {
				return "Eingabefehler: Die Variable '" + rightValue + "' ist nicht definiert.\n";
			}
		}
 
		if (leftStackPosition != null)
				return "cmp qword " + leftStackPosition + ",  " + rightVar + "     ;Führt einen Vergleich durch.";
			
		 return "Eingabefehler: Der Ausdruck '" + ctx.getText() + "' ist nicht zulässig.\n"
			+ " Bitte überprüfen Sie die Syntax in der Dokumentation.";
	}
	/**
	 * 	Verarbeitet Terme, indem es Variablen oder Konstanten zurückgibt.
     */

	@Override
	public String visitTerm(TermContext ctx) {
		if (ctx.VARIABLE() != null) {
			
			return varToRegisterMap.get(ctx.VARIABLE().getText());
		} else {
			return ctx.CONSTANT().getText();
		}
	}
/**
 * Ordnet Variablen Speicherplätze im Register zu, falls diese noch nicht zugewiesen wurden.
 * */

	private void assignVarToRegister(String varName) {
		if (!varToRegisterMap.containsKey(varName)) {

			varToRegisterMap.put(varName, "[rsp + " + stackOffset + "]");

			stackOffset += 16;
		}
	}

	/**
	 * Verarbeitet Loop-Anweisungen im Code und übersetzt sie in entsprechende Sequenzen.
	 * */
	
	@Override
	public String visitLoop(LoopContext ctx) {
		StringBuilder assemblyCode = new StringBuilder();

		String loopValue = visit(ctx.term());
		if (loopValue == null) {
			 return "Eingabefehler: Unbekannte Variable referenziert.\n"
			 		+ "Bitte deklarieren Sie alle Variablen vorab.\n";
		}
		int currentLoop = loopCounter++;
		String startLabel = "loop_start_" + currentLoop;
		String endLabel = "loop_end_" + currentLoop;
		assemblyCode.append("\n; Beginn einer LOOP-Schleifenstruktur.").append("\n");
		assemblyCode.append("mov rbx, ").append(loopValue).append("          ;RBX erhält den Wert aus dem Speicher.")
		.append("\n");
		assemblyCode.append("test rbx, rbx").append("               ;Überprüft, ob RBX gleich Null ist.").append("\n");
		assemblyCode.append("jle ").append(endLabel)
		.append("				;Beendet die Schleife, wenn RBX kleiner oder gleich Null ist.").append("\n");
		assemblyCode.append(startLabel).append(":\n").append("\n");
		assemblyCode.append(visit(ctx.program())).append("\n");

		assemblyCode.append("dec rbx").append("                     ;RBX um 1 verringern.").append("\n");
		assemblyCode.append("test rbx, rbx").append("               ;Testet RBX erneut.").append("\n");
		assemblyCode.append("jg ").append(startLabel).append("             ;Fortsetzung, falls RBX > 0.").append("\n");

		assemblyCode.append(endLabel).append(":").append("                 ;Ende der LOOP-Schleife.").append("\n");
		;

		return assemblyCode.toString();
	}
	
	/**
	 * Berechnet Stackkapazität  für lokale Variablen zu Beginn der Ausführung.
	 */

	private void calculateStackCapacity(ProgramContext ctx) {
		for (WhilelangParser.StatementContext statement : ctx.statement()) {
			if (statement.assignment() != null) {
				assignVarToRegister(statement.assignment().VARIABLE().getText());
				

			}
			
		
		} 
	}
	/**
	 * Verarbeitet "print"-Anweisungen und übersetzt sie für die Ausgabe.
	 */

	@Override
	public String visitPrint(PrintContext ctx) {
		StringBuilder assemblyCode = new StringBuilder();
		String exprValue = ctx.expression().getText(); // Variable x, y, z
		String value = visit(ctx.expression()); // Address [rsp + 0]

		
		String printValue = "";
		if (varToRegisterMap.containsKey(exprValue)) {
			printValue = varToRegisterMap.get(exprValue);
		} else {
			printValue = exprValue;
			if (!exprValue.chars().allMatch(Character::isDigit)) {
				 return "Eingabefehler: Die Variable '" + exprValue + "' ist nicht definiert.\n";
			}
		}

		assemblyCode.append("; Ergebnis ausgeben (Print) \n");
		assemblyCode.append("mov rdi, out_format         ;Legt den Formatstring fest.\n");
		assemblyCode.append("mov rsi, " + value + "          ;Wert, der ausgegeben wird.\n");
		assemblyCode.append("call _printf                ;Ruft printf auf.\n");

		return assemblyCode.toString();
	}

	public int getstackCapacity() {
		return stackCapacity;
	}

	public void setstackCapacity(int stackCapacity) {
		this.stackCapacity = stackCapacity;
	}
 /**
  * Überprüft, ob der übergebene String eine gültige Ganzzahl darstellt.
  */
	public static boolean isInt(String string) {
		try {
			Integer.parseInt(string);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}
}