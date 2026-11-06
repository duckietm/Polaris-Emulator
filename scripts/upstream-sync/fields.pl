#!/usr/bin/perl
# Usage: perl fields.pl <owners.txt> <file>...   (code only: strings and chars are copied untouched)
use strict;
use warnings;

my ($ownersFile, @files) = @ARGV;
open my $in, '<', $ownersFile or die $!;
my %owner = map { s/\r?\n$//r => 1 } <$in>;
close $in;

my %field = (habbo => 'user', habboId => 'userId', habbosToSendEnter => 'usersToSendEnter');
my %const = (
    HABBO_CLUB => 'CLUB', WINDOW_HABBO_CLUB => 'WINDOW_CLUB', TILE_HAS_HABBOS => 'TILE_HAS_USERS',
    BOT_REACHED_HABBO => 'BOT_REACHED_USER', FURNI_HAVE_HABBO => 'FURNI_HAVE_USER',
    NOT_FURNI_HAVE_HABBO => 'NOT_FURNI_HAVE_USER', KICKED_HABBO => 'KICKED_USER',
    CONDITION_HABBO_COUNT_LEGACY => 'CONDITION_USER_COUNT_LEGACY',
    CONDITION_NOT_HABBO_COUNT_LEGACY => 'CONDITION_NOT_USER_COUNT_LEGACY',
    EFFECT_MUTE_HABBO_LEGACY => 'EFFECT_MUTE_USER_LEGACY');
my $consts = join '|', sort { length $b <=> length $a } keys %const;

sub code {
    my ($s, $isOwner) = @_;
    $s =~ s{\b($consts)\b}{$const{$1}}g;
    # Member access from outside: receiver.habbo, never a method call.
    if (my $receivers = $ENV{FIELD_RECEIVERS}) {
        $s =~ s{\b($receivers)\.(habbo|habboId|habbosToSendEnter)\b(?!\s*\()}{$1.$field{$2}}g;
    } else {
        $s =~ s{(?<!\bthis)\.(habbo|habboId|habbosToSendEnter)\b(?!\s*\()}{.$field{$1}}g;
    }
    if ($isOwner) {
        $s =~ s{\bthis\.(habbo|habboId|habbosToSendEnter)\b}{this.$field{$1}}g;
        $s =~ s{^(\s*(?:public|protected)\s+(?:final\s+)?(?:static\s+)?[A-Za-z<>]+\s+)(habbo|habboId|habbosToSendEnter)(\s*[;=])}{$1$field{$2}$3}mg;
    }
    return $s;
}

for my $file (@files) {
    open my $fh, '<:raw', $file or die "$file: $!";
    local $/;
    my $text = <$fh>;
    close $fh;
    my $isOwner = exists $owner{$file};
    my $out = '';
    while ($text =~ /\G(.*?)(\/\/[^\n]*|\/\*[\s\S]*?\*\/|"""[\s\S]*?"""|"(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])+'|$)/gcs) {
        my ($code, $tok) = ($1, $2);
        $out .= code($code, $isOwner);
        $out .= ($tok =~ m{^/[/*]}) ? code($tok, $isOwner) : $tok;
        last if $tok eq '' && pos($text) >= length $text;
    }
    if ($out ne $text) {
        open my $o, '>:raw', $file or die $!;
        print $o $out;
        close $o;
        print "$file\n";
    }
}
