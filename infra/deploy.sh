#!/usr/bin/env bash
# Usage: ./infra/deploy.sh [dev|prod] [aws-profile]
# Requires: aws CLI, python3 (for zip check)
set -euo pipefail

STAGE="${1:-prod}"
PROFILE="${2:-default}"
REGION="us-east-1"
APP="ugocalc"
STACK="${APP}-${STAGE}"
S3_BUCKET="${APP}-lambda-deployments-${STAGE}"
ZIP="ugocalc-lambda.zip"

echo "=== UgoCalc deploy: stage=${STAGE} profile=${PROFILE} ==="

# 1. Build Lambda zip from lambda/ directory
echo "--- Packing Lambda zip ---"
cd "$(dirname "$0")/../lambda"
# Install prod deps only
npm ci --omit=dev
# Zip: include source + node_modules, exclude tests and dev files
rm -f "../infra/${ZIP}"
zip -r "../infra/${ZIP}" . \
  --exclude "*.test.js" \
  --exclude "__tests__/*" \
  --exclude "*.md" \
  --exclude ".git*"
cd - > /dev/null
echo "    Created infra/${ZIP}"

# 2. Ensure S3 bucket exists
echo "--- Ensuring S3 bucket ${S3_BUCKET} ---"
aws s3api head-bucket --bucket "${S3_BUCKET}" --profile "${PROFILE}" 2>/dev/null || \
  aws s3api create-bucket \
    --bucket "${S3_BUCKET}" \
    --region "${REGION}" \
    --create-bucket-configuration LocationConstraint="${REGION}" \
    --profile "${PROFILE}"
aws s3api put-bucket-versioning \
  --bucket "${S3_BUCKET}" \
  --versioning-configuration Status=Enabled \
  --profile "${PROFILE}"

# 3. Upload zip
echo "--- Uploading Lambda zip ---"
aws s3 cp "infra/${ZIP}" "s3://${S3_BUCKET}/${ZIP}" --profile "${PROFILE}"

# 4. Deploy / update CloudFormation stack
echo "--- Deploying CloudFormation stack ${STACK} ---"
aws cloudformation deploy \
  --template-file "infra/cloudformation.yaml" \
  --stack-name "${STACK}" \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides \
    AppName="${APP}" \
    Stage="${STAGE}" \
    LambdaS3Bucket="${S3_BUCKET}" \
    LambdaS3Key="${ZIP}" \
  --region "${REGION}" \
  --profile "${PROFILE}"

# 5. Print outputs — paste into amplifyconfiguration.json
echo ""
echo "=== Stack outputs (paste into app/src/main/res/raw/amplifyconfiguration.json) ==="
aws cloudformation describe-stacks \
  --stack-name "${STACK}" \
  --query "Stacks[0].Outputs[*].[OutputKey,OutputValue]" \
  --output table \
  --region "${REGION}" \
  --profile "${PROFILE}"
