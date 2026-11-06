#!/usr/bin/perl
# Resolves git merge-file conflicts: an empty side yields the other, two import blocks are both
# kept (the formatter drops duplicates), anything else takes the upstream side.
use strict;
use warnings;

for my $file (@ARGV) {
    open my $fh, '<:raw', $file or die $!;
    local $/;
    my $text = <$fh>;
    close $fh;
    my $count = 0;
    $text =~ s{^<<<<<<<[^\n]*\n(.*?)^=======\n(.*?)^>>>>>>>[^\n]*\n}{
        my ($ours, $theirs) = ($1, $2);
        $count++;
        my $imports = sub { my $s = shift; $s =~ /\S/ && $s !~ /^(?!import |\s*$).+/m };
        if ($ours !~ /\S/) { $theirs }
        elsif ($theirs !~ /\S/) { $ours }
        elsif ($imports->($ours) && $imports->($theirs)) { $ours . $theirs }
        else { $theirs }
    }gsme;
    open my $o, '>:raw', $file or die $!;
    print $o $text;
    close $o;
    print "$file: $count resolved\n";
}
