#!/bin/bash

# Test Configuration Helper for Performance Baseline
# This script provides different test suite configurations for the performance baseline workflow

echo "Performance Baseline Test Configurations"
echo "========================================"
echo ""

echo "1. FAST SUBSET (Recommended for regular baselines)"
echo "   - data:test"
echo "   - security:test"
echo "   - Estimated time: 2-5 minutes"
echo ""

echo "2. COMPREHENSIVE (For detailed analysis)"
echo "   - server-app:runContextRecreatingTests"
echo "   - server-app:runStandardTests"
echo "   - server-app:runWebsocketTests"
echo "   - data:test"
echo "   - security:test"
echo "   - Estimated time: 10-20 minutes"
echo ""

echo "3. SINGLE TEST (For quick checks)"
echo "   - data:test"
echo "   - Estimated time: 1-2 minutes"
echo ""

echo "4. MINIMAL (For debugging)"
echo "   - security:test"
echo "   - Estimated time: 30 seconds - 1 minute"
echo ""

echo "To use a different configuration:"
echo "1. Edit .github/workflows/performance-baseline.yml"
echo "2. Replace the test_suites array with one of the configurations above"
echo "3. Commit and push the changes"
echo ""

echo "Current configuration in workflow:"
grep -A 10 "test_suites=(" .github/workflows/performance-baseline.yml | head -15 