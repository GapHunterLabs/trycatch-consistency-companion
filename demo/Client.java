class Client {
    Parser parser = new Parser();

    void callers() {
        try { parser.parse("a"); } catch (ParseException e) { }
        try { parser.parse("b"); } catch (ParseException e) { }
        try { parser.parse("c"); } catch (ParseException e) { }
        try { parser.parse("d"); } catch (ParseException e) { }
        try { parser.parse("e"); } catch (ParseException e) { }
        try { parser.parse("f"); } catch (ParseException e) { }
        try { parser.parse("g"); } catch (ParseException e) { }
        parser.parse("h"); // not caught -- the outlier, should be flagged
    }
}

class Parser {
    Object parse(String s) throws ParseException {
        return null;
    }
}

class ParseException extends Exception { }
