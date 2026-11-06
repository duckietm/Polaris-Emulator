#!/usr/bin/perl
# Usage: perl rename.pl <types.tsv> <methods.tsv> <file>...
# Java files: identifiers renamed in code; inside string literals only qualified names,
# paths, "Name.java" and literals that are exactly a name. Other files: string rules only.
use strict;
use warnings;

my ($typesFile, $methodsFile, @files) = @ARGV;
my (%type, %method);
for ([$typesFile, \%type], [$methodsFile, \%method]) {
    my ($file, $map) = @$_;
    open my $in, '<', $file or die "$file: $!";
    while (<$in>) { s/\r?\n$//; my ($old, $new) = split /\t/; $map->{$old} = $new if $old ne $new; }
    close $in;
}
my $types = join '|', sort { length $b <=> length $a } keys %type;
my $methods = join '|', sort { length $b <=> length $a } keys %method;
my %name = (%type, %method);

sub package_rules {
    my ($s, $code) = @_;
    $s =~ s{\b(polaris[./](?:hotel|messages[./](?:incoming|outgoing))[./])habboway\b}{${1}hotelway}g;
    $s =~ s{(?<![\w.])((?:hotel|messages/(?:incoming|outgoing))/)habboway/}{${1}hotelway/}g;
    return $s;
}

my $compound = join '|', sort { length $b <=> length $a } grep { $_ ne 'Habbo' } keys %name;

sub glued {
    my ($s) = @_;
    return $s if $compound eq '';
    $s =~ s{($compound)}{$name{$1}}g;
    return $s;
}

sub in_string {
    my ($s) = @_;
    return $s if $ENV{CODE_ONLY};
    return "\"$name{$1}\"" if $s =~ /^"($types|$methods)"$/;
    $s = package_rules($s);
    $s =~ s{(?<=[./\$#])($types)\b}{$type{$1}}g;
    $s =~ s{\b($types)(?=\.java\b)}{$type{$1}}g;
    $s =~ s{(?<=[#.])($methods)(?=\()}{$method{$1}}g;
    # Compound names and method names never occur in prose, so they are code wherever they appear.
    $s =~ s{\b($types|$methods)\b}{$1 eq 'Habbo' ? $1 : $name{$1}}ge;
    # Compound names glued to other code (whitespace-stripped source fragments, trace lines).
    $s = glued($s);
    # The bare type only where it is unmistakably code: declarations, parameters, generics, constructors.
    if (exists $type{Habbo}) {
        my $u = $type{Habbo};
        $s =~ s{\bHabbo(?= [a-z]\w*\s*[=;,):])}{$u}g;
        $s =~ s{(?<=[(<])Habbo(?=[ )>,\[])}{$u}g;
        $s =~ s{(?<=, )Habbo(?=[ )>,\[])}{$u}g;
        $s =~ s{(?<=new )Habbo(?=\()}{$u}g;
        $s =~ s{\bHabbo(?=::)}{$u}g;
        $s =~ s{(?<=public |private |protected |static |final )Habbo(?= )}{$u}g;
    }
    return $s;
}

sub code_tokens {
    my ($s) = @_;
    $s = package_rules($s);
    $s =~ s{\b($types|$methods)\b}{$name{$1}}g;
    return $s;
}

# In comments the bare word "Habbo" usually names the official game, so it is renamed only
# where it is clearly the type: {@link Habbo}, {@code Habbo}, Habbo#member, Habbo.member, x.Habbo.
sub comment_tokens {
    my ($s) = @_;
    $s = package_rules($s);
    $s =~ s{\b($types|$methods)\b}{$1 eq 'Habbo' ? $1 : $name{$1}}ge;
    $s =~ s{(?<=\{\@link )Habbo\b|(?<=\{\@linkplain )Habbo\b|(?<=\{\@code )Habbo\b|\bHabbo(?=[#.][a-z])|(?<=[.#])Habbo\b}{$type{Habbo}}g
        if exists $type{Habbo};
    return $s;
}

sub in_code {
    my ($s) = @_;
    my $out = '';
    while ($s =~ /\G(.*?)(\/\/[^\n]*|\/\*[\s\S]*?\*\/|$)/gcs) {
        my ($code, $comment) = ($1, $2);
        $out .= code_tokens($code);
        $out .= comment_tokens($comment) if $comment ne '';
        last if $comment eq '' && pos($s) >= length $s;
    }
    return $out;
}

for my $file (@files) {
    open my $in, '<:raw', $file or die "$file: $!";
    local $/;
    my $text = <$in>;
    close $in;
    my $out;
    if ($file =~ /\.java$/) {
        $out = '';
        while ($text =~ /\G(.*?)(\/\/[^\n]*|\/\*[\s\S]*?\*\/|"""[\s\S]*?"""|"(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])+'|$)/gcs) {
            my ($code, $tok) = ($1, $2);
            $out .= code_tokens($code);
            if ($tok =~ m{^/[/*]}) { $out .= comment_tokens($tok); }
            elsif ($tok =~ /^"/) { $out .= in_string($tok); }
            else { $out .= $tok; }
            last if $tok eq '' && pos($text) >= length $text;
        }
    } else {
        $out = $text;
        $out =~ s{("(?:\\.|[^"\\\n])*")}{in_string($1)}ge;
        $out =~ s{(?<=[./\$#])($types)\b}{$type{$1}}g;
        $out =~ s{(?<=[#.])($methods)(?=\()}{$method{$1}}g;
        $out = package_rules($out);
        # Plain-text fixtures (traces, frozen matrices) name classes and methods bare; the JSON
        # packet contract also holds renderer names, so it keeps the whole-word rules only.
        $out = glued($out) unless $file =~ /\.json$/;
    }
    if ($out ne $text) {
        open my $o, '>:raw', $file or die "$file: $!";
        print $o $out;
        close $o;
        print "$file\n";
    }
}
