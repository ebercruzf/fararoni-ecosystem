#!/usr/bin/env perl
# cleanup_comments.pl — Automated comment cleanup for Fararoni Java files (ETAPA 8B-8H)
# Usage: perl cleanup_comments.pl [--dry-run] [directory]
use strict;
use warnings;
use File::Find;
use File::Basename;
use Cwd 'abs_path';
use POSIX qw(strftime);

# --- Config ---
my $dry_run = 0;
my $base_dir;

for my $arg (@ARGV) {
    if ($arg eq '--dry-run') {
        $dry_run = 1;
    } else {
        $base_dir = $arg;
    }
}

$base_dir //= '/Users/ecruz/Documents/Proyectos/DevLlM/FararoniEcoLib/fararoni-core/src/main/java/dev/fararoni/core/';

# Skip core/core/ when running on parent dir (disable with --no-skip)
my $skip_core_core = grep { $_ eq '--no-skip' } @ARGV ? 0 : 1;

my $log_path = '/Users/ecruz/Documents/Proyectos/DevLlM/FararoniEcoLib/fararoni-core/cleanup_log.md';
my @log_entries;
my $files_processed = 0;
my $files_modified  = 0;
my $files_skipped   = 0;
my $total_lines_removed = 0;

# --- Find all .java files, excluding 8A dirs ---
my @java_files;
find(sub {
    return unless -f && /\.java$/;
    my $rel = $File::Find::name;
    # Skip core/core/ directory (already cleaned)
    return if $skip_core_core && $rel =~ m{/core/core/};
    push @java_files, $File::Find::name;
}, $base_dir);

@java_files = sort @java_files;
my $total_files = scalar @java_files;
print "Found $total_files Java files to process" . ($dry_run ? " (DRY RUN)" : "") . "\n";

# --- Process each file ---
for my $filepath (@java_files) {
    $files_processed++;
    my $short_path = $filepath;
    $short_path =~ s{.*/core/core/}{core/};

    open my $fh, '<', $filepath or do {
        warn "Cannot read $filepath: $!\n";
        next;
    };
    my @original_lines = <$fh>;
    close $fh;

    my $original_count = scalar @original_lines;
    my @result_lines = process_file(\@original_lines);
    my $result_count = scalar @result_lines;

    my $original_text = join('', @original_lines);
    my $result_text   = join('', @result_lines);

    if ($original_text eq $result_text) {
        $files_skipped++;
        next;
    }

    $files_modified++;
    my $lines_removed = $original_count - $result_count;
    $total_lines_removed += $lines_removed;

    # Generate change details
    my @changes = generate_change_details(\@original_lines, \@result_lines);

    my $entry = "## $short_path ($original_count → $result_count lines, -$lines_removed removed)\n";
    $entry .= "### Changes:\n";
    for my $c (@changes) {
        $entry .= "- $c\n";
    }
    $entry .= "\n";
    push @log_entries, $entry;

    unless ($dry_run) {
        open my $out, '>', $filepath or do {
            warn "Cannot write $filepath: $!\n";
            next;
        };
        print $out $result_text;
        close $out;
    }

    if ($files_processed % 50 == 0 || $files_processed == $total_files) {
        printf "[%d/%d] processed, %d modified, %d skipped\n",
            $files_processed, $total_files, $files_modified, $files_skipped;
    }
}

# --- Write log ---
write_log();
print "\nDone! $files_modified files " . ($dry_run ? "would be " : "") . "modified, $files_skipped unchanged.\n";
print "Total lines removed: $total_lines_removed\n";
print "Log written to: $log_path\n";

# ============================================================
# CORE PROCESSING
# ============================================================

sub process_file {
    my ($lines_ref) = @_;
    my @lines = @$lines_ref;

    # --- PASS 0: Identify license header end line ---
    my $license_end = find_license_end(\@lines);

    # --- PASS 1: Extract @author/@version/@since from class Javadoc ---
    my ($author, $version, $since, $class_decl_idx) = extract_class_meta(\@lines, $license_end);

    # --- PASS 2: Remove block comments (/** */ and /* */) except license ---
    @lines = remove_block_comments(\@lines, $license_end);

    # --- PASS 3: Remove line comments (skip license header) ---
    # After PASS 2, license_end index may have shifted, recalculate
    my $new_license_end = find_license_end(\@lines);
    @lines = remove_line_comments(\@lines, $new_license_end);

    # --- PASS 4: Re-insert minimal class Javadoc with @author/@version/@since ---
    @lines = reinsert_class_meta(\@lines, $author, $version, $since, $license_end);

    # --- PASS 5: Clean whitespace ---
    @lines = clean_whitespace(\@lines);

    return @lines;
}

# Find the line index where the license header ends (the line with closing */)
sub find_license_end {
    my ($lines) = @_;
    # License starts at line 0 with /*
    return -1 unless @$lines && $lines->[0] =~ m{^\s*/\*};

    for my $i (0 .. $#$lines) {
        if ($lines->[$i] =~ m{\*/\s*$}) {
            return $i;
        }
    }
    return -1;  # No license found
}

# Extract @author, @version, @since from the class-level Javadoc
sub extract_class_meta {
    my ($lines, $license_end) = @_;
    my ($author, $version, $since);
    my $class_decl_idx;

    # Find the class/interface/enum/record declaration
    for my $i (($license_end + 1) .. $#$lines) {
        if ($lines->[$i] =~ /^\s*(?:public\s+)?(?:abstract\s+|final\s+|sealed\s+|non-sealed\s+)*(?:class|interface|enum|record|\@interface)\s+/) {
            $class_decl_idx = $i;
            last;
        }
    }

    return (undef, undef, undef, undef) unless defined $class_decl_idx;

    # Search backwards from class declaration for the Javadoc that precedes it
    # (skip annotations like @Deprecated, @SuppressWarnings, blank lines)
    my $javadoc_end;
    my $search_start = $class_decl_idx - 1;

    # Skip annotations and blank lines above class declaration
    while ($search_start > $license_end &&
           ($lines->[$search_start] =~ /^\s*$/ ||
            $lines->[$search_start] =~ /^\s*@\w+/)) {
        $search_start--;
    }

    # Now $search_start should point at the end of the Javadoc (line with */)
    if ($search_start > $license_end && $lines->[$search_start] =~ m{\*/\s*$}) {
        $javadoc_end = $search_start;

        # Find the start of this Javadoc
        my $javadoc_start = $javadoc_end;
        while ($javadoc_start > $license_end && $lines->[$javadoc_start] !~ m{^\s*/\*\*}) {
            $javadoc_start--;
        }

        # Extract @author, @version, @since from this Javadoc block
        for my $i ($javadoc_start .. $javadoc_end) {
            if ($lines->[$i] =~ /\@author\s+(.+)/) {
                $author = $1;
                $author =~ s/\s*\*?\s*$//;
            }
            if ($lines->[$i] =~ /\@version\s+(.+)/) {
                $version = $1;
                $version =~ s/\s*\*?\s*$//;
            }
            if ($lines->[$i] =~ /\@since\s+(.+)/) {
                $since = $1;
                $since =~ s/\s*\*?\s*$//;
            }
        }
    }

    return ($author, $version, $since, $class_decl_idx);
}

# Remove all block comments except the license header
sub remove_block_comments {
    my ($lines, $license_end) = @_;
    my @result;
    my $in_block = 0;

    for my $i (0 .. $#$lines) {
        # Preserve license header as-is
        if ($i <= $license_end) {
            push @result, $lines->[$i];
            next;
        }

        if ($in_block) {
            # Look for end of block comment
            if ($lines->[$i] =~ m{\*/}) {
                $in_block = 0;
                # If there's code after the */, keep that part
                my $after = $lines->[$i];
                $after =~ s{.*?\*/}{};
                if ($after =~ /\S/) {
                    push @result, $after;
                }
            }
            # Skip lines inside block comments
            next;
        }

        # Check for block comment start on this line
        if ($lines->[$i] =~ m{^\s*/\*[\*]?\s} || $lines->[$i] =~ m{^\s*/\*[\*]?$}) {
            # Single-line block comment: /* ... */ or /** ... */
            if ($lines->[$i] =~ m{/\*.*?\*/}) {
                # Single-line block comment — skip entirely
                # But check if there's code before it
                my $before = $lines->[$i];
                $before =~ s{/\*.*?\*/}{}g;
                if ($before =~ /\S/) {
                    push @result, $before . "\n";
                }
                next;
            }
            # Multi-line block comment starts here
            $in_block = 1;
            # Check if there's code before the /*
            my $before = $lines->[$i];
            $before =~ s{/\*[\*]?.*}{};
            if ($before =~ /\S/) {
                push @result, $before . "\n";
            }
            next;
        }

        # Handle inline block comments: code /* comment */ more_code
        if ($lines->[$i] =~ m{/\*.*?\*/}) {
            my $line = $lines->[$i];
            # Remove inline block comments but protect strings
            $line = remove_inline_block_comments($line);
            push @result, $line;
            next;
        }

        push @result, $lines->[$i];
    }

    return @result;
}

# Remove inline /* ... */ comments while protecting string literals
sub remove_inline_block_comments {
    my ($line) = @_;
    my $result = '';
    my $in_string = 0;
    my $string_char = '';
    my $i = 0;
    my @chars = split //, $line;

    while ($i <= $#chars) {
        if ($in_string) {
            $result .= $chars[$i];
            if ($chars[$i] eq '\\') {
                # Skip escaped character
                $i++;
                $result .= $chars[$i] if $i <= $#chars;
            } elsif ($chars[$i] eq $string_char) {
                $in_string = 0;
            }
        } elsif ($chars[$i] eq '"' || $chars[$i] eq '\'') {
            $in_string = 1;
            $string_char = $chars[$i];
            $result .= $chars[$i];
        } elsif ($chars[$i] eq '/' && $i + 1 <= $#chars && $chars[$i+1] eq '*') {
            # Start of inline block comment — find the end
            my $j = $i + 2;
            while ($j + 1 <= $#chars) {
                if ($chars[$j] eq '*' && $chars[$j+1] eq '/') {
                    $j += 2;
                    last;
                }
                $j++;
            }
            $i = $j;
            next;
        } else {
            $result .= $chars[$i];
        }
        $i++;
    }
    return $result;
}

# Remove single-line comments (skip license header)
sub remove_line_comments {
    my ($lines, $license_end) = @_;
    $license_end //= -1;
    my @result;

    for my $i (0 .. $#$lines) {
        my $line = $lines->[$i];

        # Preserve license header lines as-is
        if ($i <= $license_end) {
            push @result, $line;
            next;
        }

        # Full-line comment (only whitespace before //)
        if ($line =~ /^\s*\/\//) {
            next;  # Remove entire line
        }

        # Trailing comment — remove // but protect strings
        if ($line =~ /\/\//) {
            my $cleaned = strip_trailing_comment($line);
            push @result, $cleaned;
            next;
        }

        push @result, $line;
    }

    return @result;
}

# Strip trailing // comment while protecting string literals
sub strip_trailing_comment {
    my ($line) = @_;
    my $in_string = 0;
    my $string_char = '';
    my @chars = split //, $line;
    my $i = 0;

    while ($i <= $#chars) {
        if ($in_string) {
            if ($chars[$i] eq '\\') {
                $i += 2;
                next;
            }
            if ($chars[$i] eq $string_char) {
                $in_string = 0;
            }
            $i++;
        } elsif ($chars[$i] eq '"' || $chars[$i] eq '\'') {
            $in_string = 1;
            $string_char = $chars[$i];
            $i++;
        } elsif ($chars[$i] eq '/' && $i + 1 <= $#chars && $chars[$i+1] eq '/') {
            # Found trailing comment outside of strings
            my $code_part = substr($line, 0, $i);
            $code_part =~ s/\s+$//;  # Trim trailing whitespace
            return $code_part . "\n";
        } else {
            $i++;
        }
    }

    return $line;
}

# Re-insert @author/@version/@since as minimal Javadoc before class declaration
sub reinsert_class_meta {
    my ($lines, $author, $version, $since, $license_end) = @_;

    # If no metadata was extracted, nothing to insert
    return @$lines unless ($author || $version || $since);

    # Find the class declaration again in the cleaned lines
    my $class_idx;
    for my $i (0 .. $#$lines) {
        if ($lines->[$i] =~ /^\s*(?:public\s+)?(?:abstract\s+|final\s+|sealed\s+|non-sealed\s+)*(?:class|interface|enum|record|\@interface)\s+/) {
            $class_idx = $i;
            last;
        }
    }
    return @$lines unless defined $class_idx;

    # Check if there's already a Javadoc with @author above the class declaration
    # (in case the file was already partially cleaned)
    my $check = $class_idx - 1;
    while ($check >= 0 && ($lines->[$check] =~ /^\s*$/ || $lines->[$check] =~ /^\s*@\w+/)) {
        $check--;
    }
    if ($check >= 0 && $lines->[$check] =~ m{\*/}) {
        # There's already a Javadoc — check if it has @author
        my $j = $check;
        while ($j >= 0 && $lines->[$j] !~ m{/\*\*}) {
            $j--;
        }
        for my $k ($j .. $check) {
            return @$lines if $lines->[$k] =~ /\@author/;
        }
    }

    # Build minimal Javadoc
    my @javadoc;
    push @javadoc, "/**\n";
    push @javadoc, " * \@author $author\n" if $author;
    push @javadoc, " * \@version $version\n" if $version;
    push @javadoc, " * \@since $since\n" if $since;
    push @javadoc, " */\n";

    # Find insertion point — right before the class declaration (or before annotations)
    my $insert_at = $class_idx;
    # Move up past annotations
    while ($insert_at > 0 && $lines->[$insert_at - 1] =~ /^\s*@\w+/) {
        $insert_at--;
    }

    my @result;
    push @result, @{$lines}[0 .. ($insert_at - 1)];
    push @result, @javadoc;
    push @result, @{$lines}[$insert_at .. $#$lines];

    return @result;
}

# Clean up whitespace: collapse multiple blank lines, remove blanks at block boundaries
sub clean_whitespace {
    my ($lines) = @_;
    my @result;
    my $prev_blank = 0;

    for my $i (0 .. $#$lines) {
        my $line = $lines->[$i];
        my $is_blank = ($line =~ /^\s*$/);

        if ($is_blank) {
            # Don't allow blank line right after opening brace
            if (@result && $result[-1] =~ /\{\s*$/) {
                next;
            }
            # Don't allow multiple consecutive blank lines
            if ($prev_blank) {
                next;
            }
            $prev_blank = 1;
        } else {
            # Don't allow blank line right before closing brace
            if ($line =~ /^\s*\}/ && @result && $result[-1] =~ /^\s*$/) {
                pop @result;
            }
            $prev_blank = 0;
        }

        push @result, $line;
    }

    # Remove trailing blank lines
    while (@result && $result[-1] =~ /^\s*$/) {
        pop @result;
    }
    # Ensure file ends with newline
    if (@result && $result[-1] !~ /\n$/) {
        $result[-1] .= "\n";
    }

    return @result;
}

# ============================================================
# CHANGE LOG
# ============================================================

sub generate_change_details {
    my ($orig, $result) = @_;
    my @changes;

    # Compare original vs result to identify what was removed
    my %removed_types;

    my $in_block = 0;
    my $block_type = '';
    my $block_start = 0;
    my $license_end = find_license_end($orig);

    for my $i (0 .. $#$orig) {
        next if $i <= $license_end;
        my $line = $orig->[$i];

        if ($in_block) {
            if ($line =~ m{\*/}) {
                my $lnum = $block_start + 1;
                my $lend = $i + 1;
                $removed_types{"$block_type (lines $lnum-$lend)"} = 1;
                $in_block = 0;
            }
            next;
        }

        # Javadoc block start
        if ($line =~ m{^\s*/\*\*} && $line !~ m{\*/}) {
            $in_block = 1;
            $block_type = 'Javadoc block';
            $block_start = $i;
            next;
        }
        # Block comment start
        if ($line =~ m{^\s*/\*} && $line !~ m{\*/}) {
            $in_block = 1;
            $block_type = 'Block comment';
            $block_start = $i;
            next;
        }
        # Single-line Javadoc
        if ($line =~ m{^\s*/\*\*.*\*/}) {
            my $lnum = $i + 1;
            $removed_types{"Single-line Javadoc (line $lnum)"} = 1;
            next;
        }
        # Single-line block comment
        if ($line =~ m{^\s*/\*.*\*/}) {
            my $lnum = $i + 1;
            $removed_types{"Single-line block comment (line $lnum)"} = 1;
            next;
        }
        # Full-line // comment
        if ($line =~ /^\s*\/\//) {
            my $lnum = $i + 1;
            my $preview = $line;
            $preview =~ s/^\s*\/\/\s?//;
            $preview =~ s/\s+$//;
            if (length($preview) > 60) {
                $preview = substr($preview, 0, 57) . '...';
            }
            if ($line =~ /^\s*\/\/\s*[=\-═─]{4,}/) {
                $removed_types{"Section header (line $lnum)"} = 1;
            } else {
                $removed_types{"Line comment (line $lnum): \"$preview\""} = 1;
            }
            next;
        }
        # Trailing comment
        if ($line =~ /\/\// && !($line =~ /^\s*\/\//)) {
            # Check if it's actually a comment (not inside string)
            my $cleaned = strip_trailing_comment($line);
            if ($cleaned ne $line) {
                my $lnum = $i + 1;
                $removed_types{"Trailing comment stripped (line $lnum)"} = 1;
            }
        }
    }

    # Summarize instead of listing every single line
    my $javadoc_count = scalar grep { /^Javadoc block/ } keys %removed_types;
    my $block_count = scalar grep { /^Block comment/ } keys %removed_types;
    my $line_count = scalar grep { /^Line comment/ } keys %removed_types;
    my $section_count = scalar grep { /^Section header/ } keys %removed_types;
    my $trailing_count = scalar grep { /^Trailing comment/ } keys %removed_types;
    my $single_jd = scalar grep { /^Single-line Javadoc/ } keys %removed_types;
    my $single_bc = scalar grep { /^Single-line block comment/ } keys %removed_types;

    push @changes, "$javadoc_count Javadoc block(s) removed" if $javadoc_count;
    push @changes, "$single_jd single-line Javadoc(s) removed" if $single_jd;
    push @changes, "$block_count block comment(s) removed" if $block_count;
    push @changes, "$single_bc single-line block comment(s) removed" if $single_bc;
    push @changes, "$line_count line comment(s) removed" if $line_count;
    push @changes, "$section_count section header(s) removed" if $section_count;
    push @changes, "$trailing_count trailing comment(s) stripped" if $trailing_count;
    push @changes, "Whitespace cleaned" if scalar @$orig != scalar @$result;

    push @changes, "No significant changes detected" unless @changes;

    return @changes;
}

sub write_log {
    open my $fh, '>', $log_path or die "Cannot write log: $!\n";
    my $date = strftime("%Y-%m-%d %H:%M:%S", localtime);
    my $mode = $dry_run ? "DRY RUN" : "APPLIED";

    print $fh "# Cleanup Log — ETAPA 8B-8H ($mode)\n";
    print $fh "Generated: $date\n\n";
    print $fh "**Total files scanned**: $files_processed\n";
    print $fh "**Files modified**: $files_modified\n";
    print $fh "**Files unchanged**: $files_skipped\n";
    print $fh "**Total lines removed**: $total_lines_removed\n\n";
    print $fh "---\n\n";

    for my $entry (@log_entries) {
        print $fh $entry;
    }

    close $fh;
}
